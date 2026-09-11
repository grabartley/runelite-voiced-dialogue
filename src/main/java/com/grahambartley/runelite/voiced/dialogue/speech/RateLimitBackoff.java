package com.grahambartley.runelite.voiced.dialogue.speech;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RateLimitBackoff {

  private static final long BACKOFF_BASE_MILLIS = 1_000;

  private static final long BACKOFF_MAX_MILLIS = 30_000;

  private static final long HINT_MAX_MILLIS = 60 * 60 * 1_000L;

  private static final long NANOS_PER_MILLI = 1_000_000L;

  private final AtomicReference<Window> window = new AtomicReference<>(Window.clear(0));

  private final LongSupplier clock;

  public RateLimitBackoff() {
    this(System::nanoTime);
  }

  RateLimitBackoff(LongSupplier nanoClock) {
    this.clock = nanoClock;
  }

  long generation() {
    return window.get().generation;
  }

  boolean isThrottled() {
    return window.get().isOpen(clock.getAsLong());
  }

  boolean isRefusing() {
    Window standing = window.get();
    return standing.stated && standing.isOpen(clock.getAsLong());
  }

  void recordRateLimited(long statedWaitMillis, long observedGeneration) {
    long now = clock.getAsLong();
    long statedWait = Math.min(Math.max(statedWaitMillis, 0), HINT_MAX_MILLIS);
    Window standing =
        window.updateAndGet(current -> current.rateLimited(now, statedWait, observedGeneration));
    if (standing.generation != observedGeneration) {
      log.debug("[TTS cloud] 429 predates a call that succeeded; leaving the back-off clear");
      return;
    }
    if (statedWaitMillis > HINT_MAX_MILLIS) {
      log.warn(
          "[TTS cloud] 429 asked for a {}ms wait; clamped to {}ms",
          statedWaitMillis,
          HINT_MAX_MILLIS);
    }
    log.debug(
        "[TTS cloud] rate limited (429); {} for {}ms",
        standing.stated ? "the provider asked to be left alone" : "backing off prefetch",
        standing.remainingMillis(now));
  }

  void recordSuccess() {
    reset();
  }

  void reset() {
    window.updateAndGet(current -> Window.clear(current.generation + 1));
  }

  static long backoffWindowMillis(int consecutive) {
    int shift = Math.min(Math.max(consecutive, 1) - 1, 16);
    long window = BACKOFF_BASE_MILLIS << shift;
    return Math.min(window, BACKOFF_MAX_MILLIS);
  }

  private static final class Window {

    private final long endNanos;
    private final boolean stated;
    private final boolean open;
    private final long generation;
    private final int consecutive;

    static Window clear(long generation) {
      return new Window(0, false, false, generation, 0);
    }

    private Window(long endNanos, boolean stated, boolean open, long generation, int consecutive) {
      this.endNanos = endNanos;
      this.stated = stated;
      this.open = open;
      this.generation = generation;
      this.consecutive = consecutive;
    }

    Window rateLimited(long nowNanos, long statedWaitMillis, long observedGeneration) {
      if (generation != observedGeneration) {
        return this;
      }
      int rejections = consecutive + 1;
      boolean nowStated = statedWaitMillis > 0;
      long millis = nowStated ? statedWaitMillis : backoffWindowMillis(rejections);
      Window opened =
          new Window(nowNanos + millis * NANOS_PER_MILLI, nowStated, true, generation, rejections);
      return outranks(opened, nowNanos)
          ? new Window(endNanos, stated, open, generation, rejections)
          : opened;
    }

    boolean isOpen(long nowNanos) {
      return open && nowNanos - endNanos < 0;
    }

    long remainingMillis(long nowNanos) {
      return isOpen(nowNanos) ? (endNanos - nowNanos) / NANOS_PER_MILLI : 0;
    }

    private boolean outranks(Window proposed, long nowNanos) {
      if (!isOpen(nowNanos)) {
        return false;
      }
      if (stated != proposed.stated) {
        return stated;
      }
      return proposed.endNanos - endNanos < 0;
    }
  }
}
