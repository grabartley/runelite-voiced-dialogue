package com.grahambartley.tts;

import com.grahambartley.synthesis.BackendProvider;
import com.grahambartley.synthesis.Emotion;
import com.grahambartley.synthesis.SynthesisBackend;
import com.grahambartley.synthesis.SynthesisRequest;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Drives synthesis and playback off the game thread.
 *
 * <p>The game thread only calls {@link #speak} / {@link #interrupt}; everything heavy runs on a
 * single background thread fed by a small bounded queue. Synthesis is delegated to the active
 * {@link SynthesisBackend} via {@link BackendProvider}, and repeated lines are served from an
 * {@link LruCache} keyed on {@code (backendId, voiceKey, emotion, text)} so a different backend,
 * voice, or emotion never serves stale audio. Behind that sits an optional persistent {@link
 * DiskAudioCache} keyed on the same tuple, so lines survive across sessions and cloud backends are
 * not re-billed for audio the user has already heard. Lookup order is: in-memory LRU → disk →
 * synthesize, writing through to both tiers on a synth and promoting disk hits into memory. An
 * epoch counter makes interruption clean: bumping it on every new line and on {@link #interrupt}
 * causes any queued or in-flight work for a now-stale line to drop instead of playing.
 *
 * <p>A small in-flight registry de-duplicates concurrent synthesis: if two tasks reach the synth
 * step for the same {@code CacheKey} at once, only the first calls the backend and the second waits
 * on its result, so a billable cloud line is never paid for twice in parallel.
 */
@Slf4j
public final class DialogueAudioService {

  /**
   * Identifies a synthesized line. The active backend, the resolved voice, the (possibly
   * downgraded) emotion, and the text are all part of the identity, so the same words spoken with a
   * different backend, voice, or emotion are distinct cache entries.
   */
  record CacheKey(String backendId, String voiceKey, Emotion emotion, String text) {}

  private final BackendProvider backends;
  private final AudioOutput output;
  private final Executor executor;
  // Engine install/model-load runs here, NOT on the single synthesis thread, so a long bundle
  // download cannot block dialogue playback through an already-warm backend.
  private final Executor warmExecutor;
  private final LruCache<CacheKey, Pcm> cache;
  private final DiskAudioCache diskCache;
  private final IntSupplier volume;
  private final AtomicLong epoch = new AtomicLong();
  // Synths currently running, keyed by CacheKey, so a second task for the same line reuses the
  // pending result instead of issuing a duplicate (billable) backend call.
  private final ConcurrentHashMap<CacheKey, CompletableFuture<Pcm>> inFlight =
      new ConcurrentHashMap<>();

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
        diskCache,
        buildExecutor(queueCapacity),
        buildWarmExecutor(),
        cacheSize,
        volume);
  }

  /**
   * Test seam: lets callers inject an inline executor and disk cache so behavior is deterministic.
   * The same executor backs warm-up so tests stay single-threaded and deterministic.
   */
  DialogueAudioService(
      BackendProvider backends,
      AudioOutput output,
      DiskAudioCache diskCache,
      Executor executor,
      int cacheSize,
      IntSupplier volume) {
    this(backends, output, diskCache, executor, executor, cacheSize, volume);
  }

  private DialogueAudioService(
      BackendProvider backends,
      AudioOutput output,
      DiskAudioCache diskCache,
      Executor executor,
      Executor warmExecutor,
      int cacheSize,
      IntSupplier volume) {
    this.backends = backends;
    this.output = output;
    this.diskCache = diskCache;
    this.executor = executor;
    this.warmExecutor = warmExecutor;
    this.cache = new LruCache<>(cacheSize);
    this.volume = volume;
  }

  /**
   * Runs a one-off warm-up task (engine install/model load) on the dedicated warm-up thread, kept
   * separate from the single synthesis thread so a long engine download never blocks dialogue
   * playback through an already-warm backend.
   */
  public void prewarm(Runnable warm) {
    warmExecutor.execute(warm);
  }

  /**
   * Enqueues a line for synthesis and playback. Interrupts whatever is playing now so dialogue
   * advancement replaces the previous line rather than overlapping it.
   */
  public void speak(SynthesisRequest request) {
    if (request == null || request.text() == null || request.text().isEmpty()) {
      return;
    }
    long mine = epoch.incrementAndGet();
    output.stop();
    // Resolve the active backend now so the cache key reflects the backend that will actually run,
    // even if the user switches backend before this line reaches the pipeline thread.
    SynthesisBackend backend = backends.active();
    SynthesisRequest effective = BackendProvider.downgradeFor(backend, request);
    // Fold any backend-specific render variant (e.g. a selectable cloud model/voice) into the voice
    // key so two renderings of the same line never collide in the cache.
    String variant = backend.cacheVariant(effective);
    String voiceKey =
        variant.isEmpty() ? effective.voice().key() : effective.voice().key() + "|" + variant;
    CacheKey key = new CacheKey(backend.id(), voiceKey, effective.emotion(), effective.text());
    submit(() -> run(mine, backend, effective, key));
  }

  /** Stops current playback and drops any queued lines for the now-stale dialogue. */
  public void interrupt() {
    epoch.incrementAndGet();
    output.stop();
  }

  public void close() {
    epoch.incrementAndGet();
    output.stop();
    shutdown(executor);
    // In production the warm-up executor is a distinct instance; the test seam reuses the synthesis
    // executor, so only shut it down once.
    if (warmExecutor != executor) {
      shutdown(warmExecutor);
    }
    output.close();
  }

  private static void shutdown(Executor exec) {
    if (exec instanceof ExecutorService es) {
      es.shutdownNow();
      try {
        // Give an in-flight synth/warm-up a brief window to unwind so a plugin reload does not
        // orphan it.
        es.awaitTermination(2, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void run(long mine, SynthesisBackend backend, SynthesisRequest request, CacheKey key) {
    if (epoch.get() != mine) {
      return;
    }
    Pcm pcm = lookup(key);
    if (pcm == null) {
      // Both cache tiers missed: synthesize once (de-duped against any concurrent identical synth)
      // and write through to both tiers so the line is free next time, this session and every
      // future one.
      pcm = synthesizeDeduped(backend, request, key);
    }
    if (pcm == null) {
      return;
    }
    // Re-check after the (possibly slow) synth: the line may have been skipped meanwhile, so a
    // cloud
    // response that arrives after the dialogue advanced is dropped rather than played over the top.
    if (epoch.get() != mine) {
      return;
    }
    output.stream(pcm.getSamples(), pcm.getSampleRate(), volume.getAsInt());
  }

  /** Memory then disk lookup; a disk hit is promoted into memory. {@code null} when both miss. */
  private Pcm lookup(CacheKey key) {
    Pcm pcm = cache.get(key);
    if (pcm != null) {
      log.debug(
          "Serving \"{}\" ({}/{}) from memory cache",
          abbreviate(key.text()),
          key.backendId(),
          key.voiceKey());
      return pcm;
    }
    // Memory miss: try the persistent on-disk cache (this runs on the pipeline thread, never the
    // game thread). A disk hit is promoted into memory so subsequent replays skip the disk read.
    if (diskCache != null) {
      pcm = diskCache.get(key.backendId(), key.voiceKey(), key.emotion(), key.text());
      if (pcm != null) {
        cache.put(key, pcm);
        log.debug(
            "Serving \"{}\" ({}/{}) from disk cache",
            abbreviate(key.text()),
            key.backendId(),
            key.voiceKey());
      }
    }
    return pcm;
  }

  /**
   * Synthesizes the line, ensuring at most one backend call per key runs at a time. The first
   * caller for a key registers a pending result, synthesizes, writes through to both cache tiers,
   * and publishes it; a caller that finds a synth already in flight for the same key waits on it
   * instead of issuing a second (billable) backend call. Returns {@code null} on synth failure.
   */
  Pcm synthesizeDeduped(SynthesisBackend backend, SynthesisRequest request, CacheKey key) {
    CompletableFuture<Pcm> own = new CompletableFuture<>();
    CompletableFuture<Pcm> running = inFlight.putIfAbsent(key, own);
    if (running != null) {
      log.debug(
          "Reusing in-flight synthesis for \"{}\" ({}/{})",
          abbreviate(key.text()),
          key.backendId(),
          key.voiceKey());
      return await(running);
    }
    Pcm pcm = null;
    try {
      pcm = backends.synthesizeWith(backend, request);
      if (pcm != null) {
        cache.put(key, pcm);
        if (diskCache != null) {
          diskCache.put(key.backendId(), key.voiceKey(), key.emotion(), key.text(), pcm);
        }
      }
    } finally {
      // Publish before deregistering so a waiter that already grabbed this future is never left
      // blocked, and a fresh request right after sees a populated cache rather than re-synthing.
      own.complete(pcm);
      inFlight.remove(key, own);
    }
    return pcm;
  }

  private static Pcm await(CompletableFuture<Pcm> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (ExecutionException e) {
      return null;
    }
  }

  private void submit(Runnable task) {
    try {
      executor.execute(task);
    } catch (RejectedExecutionException ignored) {
      // Queue saturated or shutting down; dropping is fine since newer lines supersede older ones.
    }
  }

  private static ExecutorService buildExecutor(int queueCapacity) {
    return new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(queueCapacity),
        r -> {
          Thread t = new Thread(r, "dialogue-audio");
          t.setDaemon(true);
          return t;
        },
        // Drop the oldest queued line under backpressure (newer dialogue supersedes it), but log it
        // so QA can tell whether the queue is actually saturating in practice.
        (r, exec) -> {
          log.debug("Dialogue audio queue saturated; dropping oldest queued line");
          new ThreadPoolExecutor.DiscardOldestPolicy().rejectedExecution(r, exec);
        });
  }

  /**
   * A single-thread executor dedicated to engine warm-up (install/download/model load). Separate
   * from the synthesis executor so a multi-minute engine download cannot stall dialogue playback;
   * its queue is unbounded because warm-up tasks are few (one per backend) and must never be
   * dropped under synthesis backpressure.
   */
  private static ExecutorService buildWarmExecutor() {
    return new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        r -> {
          Thread t = new Thread(r, "dialogue-warm");
          t.setDaemon(true);
          return t;
        });
  }

  private static String abbreviate(String text) {
    return text.length() <= 40 ? text : text.substring(0, 40) + "...";
  }
}
