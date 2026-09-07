package com.grahambartley.runelite.voiced.dialogue.speech;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks the cloud backend's rate-limit (429) back-off. A 429 opens a window that grows
 * geometrically per consecutive hit and is capped so prefetch never retry-storms; any clean call
 * clears it so prefetch resumes immediately.
 *
 * <p>A rejection that states its own wait is honoured instead of guessed at. The two windows mean
 * different things and gate different work: a computed window is a guess that speculation stands
 * down for, while a stated one is the provider naming the moment it will serve again, so nothing is
 * sent until it passes. Against a wait of minutes, a guess measured in seconds only buys another
 * rejection.
 *
 * <p>Deadlines are measured on a monotonic clock, since a wall clock stepping backwards would hold
 * a window open past the wait it was meant to enforce, and a stated window admits no call that
 * could clear it. The trade is that time spent suspended does not count towards a window, so a
 * machine slept mid-wait resumes still holding it; the ceiling below bounds how long that can last.
 */
@Slf4j
public final class RateLimitBackoff {

  /** Base 429 back-off; doubled per consecutive limit hit and capped so prefetch never storms. */
  private static final long BACKOFF_BASE_MILLIS = 1_000;

  private static final long BACKOFF_MAX_MILLIS = 30_000;

  /**
   * Ceiling on an honoured wait. A quota that resets on a daily boundary can state hours, and a
   * malformed one can state anything at all; past this the window is clamped so a single response
   * cannot mute the plugin for a whole session. Reaching the clamp costs one more rejection, which
   * reopens the window, rather than silence with no way back.
   */
  private static final long HINT_MAX_MILLIS = 60 * 60 * 1_000L;

  private static final long NANOS_PER_MILLI = 1_000_000L;

  /**
   * The open back-off window, held as one value: the deadline and whether the provider stated it
   * are read together on every decision, so two overlapping 429s cannot leave one rejection's
   * stated flag paired with the other's deadline.
   */
  private final AtomicReference<Window> window = new AtomicReference<>(Window.CLEAR);

  /** Consecutive 429s, so the window grows geometrically and resets on a clean call. */
  private final AtomicInteger consecutive429 = new AtomicInteger();

  private final LongSupplier clock;

  public RateLimitBackoff() {
    this(System::nanoTime);
  }

  /** Test seam: drives the window off a supplied monotonic clock, in nanoseconds. */
  RateLimitBackoff(LongSupplier nanoClock) {
    this.clock = nanoClock;
  }

  boolean isThrottled() {
    return window.get().isOpen(clock.getAsLong());
  }

  /**
   * Whether the provider has stated a wait that has not yet passed, which is the one case where a
   * call is known to fail before it is made.
   */
  boolean isRefusing() {
    Window open = window.get();
    return open.stated && open.isOpen(clock.getAsLong());
  }

  /**
   * Opens (or widens) the back-off window after a 429. {@code statedWaitMillis} is what the
   * rejection itself asked for, or 0 when it asked for nothing, in which case the window grows
   * geometrically per repeat hit.
   */
  void recordRateLimited(long statedWaitMillis) {
    int consecutive = consecutive429.incrementAndGet();
    boolean stated = statedWaitMillis > 0;
    long millis = stated ? clamp(statedWaitMillis) : backoffWindowMillis(consecutive);
    long now = clock.getAsLong();
    Window opened = new Window(now + millis * NANOS_PER_MILLI, stated);
    // Lines and prefetch are rejected on separate threads, so the rejection that lands second does
    // not automatically own the window.
    Window standing = window.updateAndGet(current -> current.replacedBy(opened, now));
    if (stated) {
      log.debug(
          "[TTS cloud] rate limited (429); provider asked for {}ms, waiting {}ms",
          statedWaitMillis,
          standing.remainingMillis(now));
      return;
    }
    log.debug(
        "[TTS cloud] rate limited (429); backing off prefetch for {}ms",
        standing.remainingMillis(now));
  }

  /** Clears the back-off after any clean call so prefetch resumes immediately. */
  void recordSuccess() {
    reset();
  }

  /**
   * Drops the back-off outright, for a key or provider change. A window is only ever evidence about
   * the credentials that earned it, so changing either makes it meaningless, and switching provider
   * is one of the two fixes a stated window's notice asks for. The other fix, raising the quota at
   * the provider, changes nothing this plugin can observe, so it waits out the window or the
   * ceiling below, whichever comes first.
   */
  void reset() {
    window.set(Window.CLEAR);
    consecutive429.set(0);
  }

  /**
   * The back-off window for the n-th consecutive 429: {@code base * 2^(n-1)}, capped, so repeated
   * limits widen the pause geometrically instead of retry-storming, and a single 429 pauses only
   * briefly.
   */
  static long backoffWindowMillis(int consecutive) {
    int shift = Math.min(Math.max(consecutive, 1) - 1, 16);
    long window = BACKOFF_BASE_MILLIS << shift;
    return Math.min(window, BACKOFF_MAX_MILLIS);
  }

  /** A stated wait held to the ceiling, logging when the provider asked for more than that. */
  private static long clamp(long statedWaitMillis) {
    if (statedWaitMillis <= HINT_MAX_MILLIS) {
      return statedWaitMillis;
    }
    log.warn(
        "[TTS cloud] 429 asked for a {}ms wait; clamped to {}ms",
        statedWaitMillis,
        HINT_MAX_MILLIS);
    return HINT_MAX_MILLIS;
  }

  /** One back-off window: when it ends, and whether the provider named that moment itself. */
  private static final class Window {

    /** No window at all, which no deadline can be mistaken for. */
    static final Window CLEAR = new Window(0, false, false);

    private final long endNanos;
    private final boolean stated;
    private final boolean open;

    Window(long endNanos, boolean stated) {
      this(endNanos, stated, true);
    }

    private Window(long endNanos, boolean stated, boolean open) {
      this.endNanos = endNanos;
      this.stated = stated;
      this.open = open;
    }

    /** Subtraction rather than {@code <}, so a wrapped monotonic clock still compares correctly. */
    boolean isOpen(long nowNanos) {
      return open && nowNanos - endNanos < 0;
    }

    /** How much of this window is left, for a log line that reports what actually stands. */
    long remainingMillis(long nowNanos) {
      return isOpen(nowNanos) ? (endNanos - nowNanos) / NANOS_PER_MILLI : 0;
    }

    /**
     * Which of this window and a newly opened one should stand. A statement outranks a guess in
     * either direction, however the two deadlines compare: a shorter stated wait is still the
     * provider naming the moment it will serve again, and a longer guess is still a guess. Only two
     * windows of the same kind are compared by deadline, where the later one wins.
     */
    Window replacedBy(Window proposed, long nowNanos) {
      if (!isOpen(nowNanos)) {
        return proposed;
      }
      if (stated != proposed.stated) {
        return stated ? this : proposed;
      }
      return proposed.endNanos - endNanos < 0 ? this : proposed;
    }
  }
}
