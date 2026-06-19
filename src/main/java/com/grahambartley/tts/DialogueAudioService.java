package com.grahambartley.tts;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
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
 * single background thread fed by a small bounded queue. Repeated lines are served from an {@link
 * LruCache} keyed on {@code (text, speakerId)} so they never re-synthesize. An epoch counter makes
 * interruption clean: bumping it on every new line and on {@link #interrupt} causes any queued or
 * in-flight work for a now-stale line to drop instead of playing.
 */
@Slf4j
public final class DialogueAudioService {

  private final Synthesizer synth;
  private final AudioOutput output;
  private final Executor executor;
  private final LruCache<String, Pcm> cache;
  private final IntSupplier volume;
  private final AtomicLong epoch = new AtomicLong();

  public DialogueAudioService(
      Synthesizer synth, AudioOutput output, int cacheSize, int queueCapacity, IntSupplier volume) {
    this(synth, output, buildExecutor(queueCapacity), cacheSize, volume);
  }

  /** Test seam: lets callers inject an inline executor so behavior is deterministic. */
  DialogueAudioService(
      Synthesizer synth, AudioOutput output, Executor executor, int cacheSize, IntSupplier volume) {
    this.synth = synth;
    this.output = output;
    this.executor = executor;
    this.cache = new LruCache<>(cacheSize);
    this.volume = volume;
  }

  /** Runs a one-off warm-up task (typically the model load) on the pipeline thread. */
  public void prewarm(Runnable warm) {
    submit(warm);
  }

  /**
   * Enqueues a line for synthesis and playback. Interrupts whatever is playing now so dialogue
   * advancement replaces the previous line rather than overlapping it.
   */
  public void speak(String text, int speakerId) {
    if (text == null || text.isEmpty()) {
      return;
    }
    long mine = epoch.incrementAndGet();
    output.stop();
    String key = speakerId + "|" + text;
    submit(() -> run(mine, key, text, speakerId));
  }

  /** Stops current playback and drops any queued lines for the now-stale dialogue. */
  public void interrupt() {
    epoch.incrementAndGet();
    output.stop();
  }

  public void close() {
    epoch.incrementAndGet();
    output.stop();
    if (executor instanceof ExecutorService) {
      ((ExecutorService) executor).shutdownNow();
    }
    output.close();
  }

  private void run(long mine, String key, String text, int speakerId) {
    if (epoch.get() != mine) {
      return;
    }
    Pcm pcm = cache.get(key);
    if (pcm == null) {
      pcm = synth.synthesize(text, speakerId);
      if (pcm == null) {
        return;
      }
      cache.put(key, pcm);
    } else {
      log.debug("Serving \"{}\" (sid {}) from cache", abbreviate(text), speakerId);
    }
    // Re-check after the (possibly slow) synth: the line may have been skipped meanwhile.
    if (epoch.get() != mine) {
      return;
    }
    output.stream(pcm.getSamples(), pcm.getSampleRate(), volume.getAsInt());
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
        new ThreadPoolExecutor.DiscardOldestPolicy());
  }

  private static String abbreviate(String text) {
    return text.length() <= 40 ? text : text.substring(0, 40) + "...";
  }
}
