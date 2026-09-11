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

@Slf4j
public final class DialogueAudioService {

  private static final int SYNTH_THREADS = 2;

  private static final int PREFETCH_THREADS = 2;

  private static final int PREFETCH_QUEUE_CAPACITY = 16;

  private static final int SHUTDOWN_WAIT_SECONDS = 2;

  private final BackendProvider backends;
  private final AudioOutput output;
  private final Executor executor;
  private final Executor warmExecutor;
  private final Executor prefetchExecutor;
  private final TieredSynthesisCache cache;
  private final IntSupplier volume;
  private final AtomicLong epoch = new AtomicLong();
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

  public void prewarm(Runnable warm) {
    warmExecutor.execute(warm);
  }

  public void speak(SynthesisRequest request) {
    speak(request, false);
  }

  public void speak(SynthesisRequest request, boolean applyEcho) {
    if (request == null || request.text() == null || request.text().isEmpty()) {
      return;
    }
    long mine = epoch.incrementAndGet();
    output.stop();
    SynthesisBackend backend = backends.active();
    SynthesisRequest effective = BackendProvider.downgradeFor(backend, request);
    CacheKey key = keyFor(backend, effective);
    submitQuietly(executor, () -> run(mine, backend, effective, key, applyEcho));
  }

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

  public void cancelPrefetch() {
    prefetchEpoch.incrementAndGet();
  }

  private CacheKey keyFor(SynthesisBackend backend, SynthesisRequest effective) {
    String variant = backend.cacheVariant(effective);
    String voiceKey =
        variant.isEmpty() ? effective.voice().key() : effective.voice().key() + "|" + variant;
    return new CacheKey(backend.id(), voiceKey, effective.emotion(), effective.text());
  }

  public void interrupt() {
    epoch.incrementAndGet();
    output.stop();
  }

  public void close() {
    epoch.incrementAndGet();
    prefetchEpoch.incrementAndGet();
    output.stop();
    shutdown(executor);
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

  private void playBuffered(long mine, Pcm pcm, boolean applyEcho) {
    if (epoch.get() != mine) {
      return;
    }
    Pcm toPlay = applyEcho ? CaveEcho.apply(pcm) : pcm;
    output.stream(toPlay.getSamples(), toPlay.getSampleRate(), volume.getAsInt());
  }

  void runStreaming(long mine, SynthesisBackend backend, SynthesisRequest request, CacheKey key) {
    if (epoch.get() != mine) {
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

  private Pcm synthesizeDeduped(SynthesisBackend backend, SynthesisRequest request, CacheKey key) {
    return cache.withInFlight(
        key,
        () -> {
          long start = System.nanoTime();
          Pcm pcm = backends.synthesizeWith(backend, request);
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
        (r, exec) -> {
          log.debug("[TTS synth] queue saturated; dropping oldest queued line");
          new ThreadPoolExecutor.DiscardOldestPolicy().rejectedExecution(r, exec);
        });
  }

  private static ExecutorService buildWarmExecutor() {
    return new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        daemonThreadFactory("dialogue-warm"));
  }

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
