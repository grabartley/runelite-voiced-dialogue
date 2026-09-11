package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.audio.AudioOutput;
import com.grahambartley.runelite.voiced.dialogue.audio.CaveEcho;
import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.PcmSink;
import com.grahambartley.runelite.voiced.dialogue.cache.DiskAudioCache;
import com.grahambartley.runelite.voiced.dialogue.cache.TieredSynthesisCache;
import com.grahambartley.runelite.voiced.dialogue.cache.TieredSynthesisCache.CacheKey;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Drives synthesis and playback off the game thread.
 *
 * <p>The game thread only calls {@link #speak} / {@link #interrupt}; everything heavy runs on a
 * small background pool ({@code SYNTH_THREADS} workers) fed by a bounded queue, so a line stuck
 * retrying a slow cloud call does not stall the next line. Synthesis is delegated to the active
 * {@link SynthesisBackend} via {@link BackendProvider}, and repeated lines are served from a {@link
 * TieredSynthesisCache}, which also de-duplicates concurrent synthesis of the same line. An epoch
 * counter makes interruption clean: bumping it on every new line and on {@link #interrupt} causes
 * any queued or in-flight work for a now-stale line to drop instead of playing.
 */
@Slf4j
public final class DialogueAudioService {

  /**
   * Worker threads for the live synthesis pool. Two, sharing one queue, so a line stuck retrying a
   * slow cloud call (which is left running so its result still caches) does not block the next
   * line: the free worker picks it up. Concurrent same-key calls are still de-duped and each cloud
   * call gets its own pooled HTTP/1.1 connection, so two workers never contend on shared state.
   */
  private static final int SYNTH_THREADS = 2;

  /**
   * Hard ceiling on concurrent prefetch synths, so speculative warming never floods the backend.
   */
  private static final int PREFETCH_THREADS = 2;

  /**
   * Bounded prefetch backlog; excess speculative work is dropped rather than queued unboundedly.
   */
  private static final int PREFETCH_QUEUE_CAPACITY = 16;

  /**
   * How long a close waits for an in-flight synth or warm-up to unwind, so a plugin reload does not
   * orphan it.
   */
  private static final int SHUTDOWN_WAIT_SECONDS = 2;

  private final BackendProvider backends;
  private final AudioOutput output;
  private final Executor executor;
  // Backend warm-up (the cloud connection handshake) runs here, NOT on the synthesis pool, so it
  // can never block dialogue playback through an already-warm backend.
  private final Executor warmExecutor;
  // Speculative dialogue-tree prefetch runs here, off the synthesis pool, so warming the
  // next likely line never delays the line the player is actually hearing. Small fixed pool so no
  // more than PREFETCH_THREADS prefetch calls are ever in flight at once.
  private final Executor prefetchExecutor;
  private final TieredSynthesisCache cache;
  private final IntSupplier volume;
  private final AtomicLong epoch = new AtomicLong();
  // Bumped on dialogue close / NPC change so prefetch tasks queued for the old node drop instead of
  // spending on branches the player has already left. Separate from the playback epoch: a new
  // spoken line must never cancel prefetch, and a prefetch must never cancel playback.
  private final AtomicLong prefetchEpoch = new AtomicLong();

  public DialogueAudioService(
      BackendProvider backends,
      AudioOutput output,
      DiskAudioCache diskCache,
      int cacheSize,
      int queueCapacity,
      IntSupplier volume) {
    this(
        backends,
        output,
        new TieredSynthesisCache(cacheSize, diskCache),
        buildExecutor(queueCapacity),
        buildWarmExecutor(),
        buildPrefetchExecutor(),
        volume);
  }

  /** Test seam: every collaborator injected, so behavior is deterministic. */
  DialogueAudioService(
      BackendProvider backends,
      AudioOutput output,
      TieredSynthesisCache cache,
      Executor executor,
      Executor warmExecutor,
      Executor prefetchExecutor,
      IntSupplier volume) {
    this.backends = backends;
    this.output = output;
    this.cache = cache;
    this.executor = executor;
    this.warmExecutor = warmExecutor;
    this.prefetchExecutor = prefetchExecutor;
    this.volume = volume;
  }

  /** Runs a one-off backend warm-up task on the dedicated warm-up thread. */
  public void prewarm(Runnable warm) {
    warmExecutor.execute(warm);
  }

  /**
   * Enqueues a line for synthesis and playback. Interrupts whatever is playing now so dialogue
   * advancement replaces the previous line rather than overlapping it.
   */
  public void speak(SynthesisRequest request) {
    speak(request, false);
  }

  /**
   * As {@link #speak(SynthesisRequest)}, but renders a cave echo over the dry audio just before
   * playback when {@code applyEcho} is set. The echo is pure local DSP on a fresh buffer: both
   * cache tiers still store the dry line under the unchanged key, so toggling the effect never
   * invalidates the cache and the cloud backend is never re-billed.
   */
  public void speak(SynthesisRequest request, boolean applyEcho) {
    if (request == null || request.text() == null || request.text().isEmpty()) {
      return;
    }
    long mine = epoch.incrementAndGet();
    output.stop();
    // Resolve the active backend now so the cache key reflects the backend that will actually run
    // this line on the pipeline thread.
    SynthesisBackend backend = backends.active();
    SynthesisRequest effective = BackendProvider.downgradeFor(backend, request);
    CacheKey key = keyFor(backend, effective);
    submitQuietly(executor, () -> run(mine, backend, effective, key, applyEcho));
  }

  /**
   * Speculatively synthesizes a line the player is likely to hear next (a visible dialogue option),
   * warming both cache tiers so the real line plays from cache when it arrives. Unlike {@link
   * #speak}, it never plays audio and never touches the playback epoch, so it cannot interrupt or
   * be interrupted by the line currently playing. It shares the same in-flight dedup and both cache
   * tiers as {@link #speak}, so a prefetch and a real line for the same key never both bill the
   * backend, and an already-cached line is a cheap no-op. Safe to call from the game thread: the
   * pre-submit check reads only the in-memory tier, so the disk tier is never touched on the
   * caller's thread. Skipped when the active backend is unavailable or rate-limit backing off, so
   * speculation never piles onto a 429.
   */
  public void prefetch(SynthesisRequest request) {
    if (request == null || request.text() == null || request.text().isEmpty()) {
      return;
    }
    SynthesisBackend backend = backends.active();
    if (!backend.isAvailable() || backend.isThrottled()) {
      return;
    }
    SynthesisRequest effective = BackendProvider.downgradeFor(backend, request);
    CacheKey key = keyFor(backend, effective);
    if (cache.memoryHit(key) != null) {
      return;
    }
    long node = prefetchEpoch.get();
    submitQuietly(
        prefetchExecutor,
        () -> {
          // The player may have left this node, the backend may have hit a limit, or a real
          // line may have warmed this key while we waited in the queue: re-check all three
          // before spending.
          if (prefetchEpoch.get() != node || backend.isThrottled() || cache.lookup(key) != null) {
            return;
          }
          log.debug(
              "[TTS synth] prefetch ({}/{}) \"{}\"",
              key.backendId(),
              key.voiceKey(),
              key.textPreview());
          synthesizeDeduped(backend, effective, key);
        });
  }

  /**
   * Cancels prefetches queued for the current dialogue node (dialogue closed or NPC changed) by
   * advancing the prefetch epoch, so queued tasks drop instead of spending on a branch the player
   * has left. A prefetch already mid-flight finishes and its bytes stay cached.
   */
  public void cancelPrefetch() {
    prefetchEpoch.incrementAndGet();
  }

  /**
   * Builds the cache key for a resolved request: the active backend id, the voice key folded with
   * any backend-specific render variant (selectable cloud model/voice, profile, language), the
   * downgraded emotion, and the text. Shared by {@link #speak} and {@link #prefetch} so a
   * prefetched line and the real line resolve to the exact same key.
   */
  private CacheKey keyFor(SynthesisBackend backend, SynthesisRequest effective) {
    String variant = backend.cacheVariant(effective);
    String voiceKey =
        variant.isEmpty() ? effective.voice().key() : effective.voice().key() + "|" + variant;
    return new CacheKey(backend.id(), voiceKey, effective.emotion(), effective.text());
  }

  /** Stops current playback and drops any queued lines for the now-stale dialogue. */
  public void interrupt() {
    epoch.incrementAndGet();
    output.stop();
  }

  public void close() {
    epoch.incrementAndGet();
    prefetchEpoch.incrementAndGet();
    output.stop();
    shutdown(executor);
    // In production the warm-up and prefetch executors are distinct instances; the test seam reuses
    // the synthesis executor, so only shut a distinct one down once.
    if (warmExecutor != executor) {
      shutdown(warmExecutor);
    }
    if (prefetchExecutor != executor && prefetchExecutor != warmExecutor) {
      shutdown(prefetchExecutor);
    }
    output.close();
  }

  private static void shutdown(Executor exec) {
    if (exec instanceof ExecutorService) {
      ExecutorService es = (ExecutorService) exec;
      es.shutdownNow();
      try {
        es.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        // Shutting down anyway; the plugin does not re-raise the thread interrupt flag (a Hub
        // constraint), so just stop waiting.
        log.debug("Interrupted while awaiting executor shutdown");
      }
    }
  }

  private void run(
      long mine,
      SynthesisBackend backend,
      SynthesisRequest request,
      CacheKey key,
      boolean applyEcho) {
    if (epoch.get() != mine) {
      return;
    }
    Pcm pcm = cache.lookup(key);
    if (pcm != null) {
      playBuffered(mine, pcm, applyEcho);
      return;
    }
    // Both cache tiers missed. Stream the line (start playing as it downloads) unless a cave echo
    // applies, since the echo needs the whole clip up front and so synthesizes a full buffer first.
    if (!applyEcho) {
      runStreaming(mine, backend, request, key);
      return;
    }
    pcm = synthesizeDeduped(backend, request, key);
    if (pcm == null) {
      return;
    }
    playBuffered(mine, pcm, applyEcho);
  }

  /**
   * Plays a fully-synthesized line, dropping it if the dialogue has advanced meanwhile. This
   * re-check is what lets a slow cloud response land in the cache yet never play over the top of
   * the line that superseded it.
   */
  private void playBuffered(long mine, Pcm pcm, boolean applyEcho) {
    if (epoch.get() != mine) {
      return;
    }
    Pcm toPlay = applyEcho ? CaveEcho.apply(pcm) : pcm;
    output.stream(toPlay.getSamples(), toPlay.getSampleRate(), volume.getAsInt());
  }

  /**
   * Streams a cache-missed live line: playback starts on the first chunk and the whole line is teed
   * into both cache tiers on a clean finish. Shares the in-flight dedup with the buffered path, so
   * if a synth for this key is already running (for example a prefetch), this awaits it and plays
   * it buffered rather than issuing a second billable call. After a skip the sink stops feeding the
   * player while the backend keeps draining the body, so the finished line is still cached and
   * never re-billed on a later hearing. A line the backend deems incomplete is played but returns
   * {@code null}, so nothing clipped is persisted.
   */
  // Package-private so a concurrency test can drive the dedup-degrades-to-buffered branch directly.
  void runStreaming(long mine, SynthesisBackend backend, SynthesisRequest request, CacheKey key) {
    if (epoch.get() != mine) {
      // Superseded between run()'s check and here (e.g. a slow disk lookup): do NOT open a stream,
      // since beginStream bumps the shared playback generation and would cut the line that replaced
      // us. Still warm both cache tiers so the line is free next time, matching the buffered path,
      // where a line that goes stale mid-synth is cached but never played over the top.
      synthesizeDeduped(backend, request, key);
      return;
    }
    AtomicBoolean deduped = new AtomicBoolean();
    Pcm pcm =
        cache.withInFlight(
            key,
            () -> {
              AudioOutput.AudioStream stream = output.beginStream(volume.getAsInt());
              try {
                PcmSink sink = stream::write;
                return backends.synthesizeStreamingWith(backend, request, sink);
              } finally {
                stream.end();
              }
            },
            () -> deduped.set(true));
    if (deduped.get() && pcm != null) {
      playBuffered(mine, pcm, false);
    }
  }

  /**
   * Synthesizes the line through the shared in-flight dedup, so a key already being synthesized is
   * awaited rather than billed a second time. Returns {@code null} on synth failure.
   */
  private Pcm synthesizeDeduped(SynthesisBackend backend, SynthesisRequest request, CacheKey key) {
    return cache.withInFlight(
        key,
        () -> {
          long start = System.nanoTime();
          Pcm pcm = backends.synthesizeWith(backend, request);
          // Both cache tiers missed, so this is the real (billable, for cloud) synth: time it so
          // "slow responses" can be quantified, and note the outcome so a silent line is
          // distinguishable from a slow one.
          log.debug(
              "[TTS synth] backend={} ok={} synthMs={} ({}/{}) \"{}\"",
              backend.id(),
              pcm != null,
              elapsedMs(start),
              key.backendId(),
              key.voiceKey(),
              key.textPreview());
          return pcm;
        },
        () ->
            log.debug(
                "[TTS synth] dedup reuse ({}/{}) \"{}\"",
                key.backendId(),
                key.voiceKey(),
                key.textPreview()));
  }

  private static void submitQuietly(Executor exec, Runnable task) {
    try {
      exec.execute(task);
    } catch (RejectedExecutionException ignored) {
      // Queue saturated or shutting down; dropping is fine since newer lines supersede older ones
      // and speculative work is always safe to lose.
    }
  }

  private static ThreadFactory daemonThreadFactory(String name) {
    return r -> {
      Thread t = new Thread(r, name);
      t.setDaemon(true);
      return t;
    };
  }

  private static ExecutorService buildExecutor(int queueCapacity) {
    return new ThreadPoolExecutor(
        SYNTH_THREADS,
        SYNTH_THREADS,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(queueCapacity),
        daemonThreadFactory("dialogue-audio"),
        // Drop the oldest queued line under backpressure (newer dialogue supersedes it), but log it
        // so QA can tell whether the queue is actually saturating in practice.
        (r, exec) -> {
          log.debug("[TTS synth] queue saturated; dropping oldest queued line");
          new ThreadPoolExecutor.DiscardOldestPolicy().rejectedExecution(r, exec);
        });
  }

  /**
   * The warm-up executor. Its queue is unbounded because warm-up tasks are few and must never be
   * dropped under synthesis backpressure.
   */
  private static ExecutorService buildWarmExecutor() {
    return new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        daemonThreadFactory("dialogue-warm"));
  }

  /**
   * The prefetch executor. Its queue is bounded and the oldest queued prefetch is discarded under
   * backpressure, since speculative work for a node the player may already have left is the safest
   * thing to drop.
   */
  private static ExecutorService buildPrefetchExecutor() {
    return new ThreadPoolExecutor(
        PREFETCH_THREADS,
        PREFETCH_THREADS,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(PREFETCH_QUEUE_CAPACITY),
        daemonThreadFactory("dialogue-prefetch"),
        new ThreadPoolExecutor.DiscardOldestPolicy());
  }

  private static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }
}
