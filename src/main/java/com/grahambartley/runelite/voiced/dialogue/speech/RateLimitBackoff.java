package com.grahambartley.runelite.voiced.dialogue.speech;

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
   * The whole back-off state, held as one value so every decision reads and replaces it in a single
   * step: the deadline, whether the provider stated it, how many rejections have run consecutively,
   * and which generation of the back-off they belong to. Split across separate fields, two
   * overlapping rejections could pair one's stated flag with the other's deadline, and a rejection
   * could install a window on top of a reset it had already been checked against.
   */
  private final AtomicReference<Window> window = new AtomicReference<>(Window.clear(0));

  private final LongSupplier clock;

  public RateLimitBackoff() {
    this(System::nanoTime);
  }

  /** Test seam: drives the window off a supplied monotonic clock, in nanoseconds. */
  RateLimitBackoff(LongSupplier nanoClock) {
    this.clock = nanoClock;
  }

  /** The reading a caller passes back to {@link #recordRateLimited}, taken before it calls out. */
  long generation() {
    return window.get().generation;
  }

  boolean isThrottled() {
    return window.get().isOpen(clock.getAsLong());
  }

  /**
   * Whether the provider has stated a wait that has not yet passed, which is the one case where a
   * call is known to fail before it is made.
   */
  boolean isRefusing() {
    Window standing = window.get();
    return standing.stated && standing.isOpen(clock.getAsLong());
  }

  /**
   * Opens (or widens) the back-off window after a 429. {@code statedWaitMillis} is what the
   * rejection itself asked for, or 0 when it asked for nothing, in which case the window grows
   * geometrically per repeat hit, clamped to the ceiling. {@code observedGeneration} is the {@link
   * #generation()} read before the call went out; a rejection carrying a stale one is dropped. That
   * also drops a rejection the provider genuinely issued after the success, which costs a ladder
   * rung of speculation and corrects itself on the next rejection, where holding a stale window
   * would not correct itself at all.
   */
  void recordRateLimited(long statedWaitMillis, long observedGeneration) {
    long now = clock.getAsLong();
    long statedWait = Math.min(Math.max(statedWaitMillis, 0), HINT_MAX_MILLIS);
    // Lines and prefetch are rejected on separate threads, so the rejection that lands second does
    // not automatically own the window, and one that predates a reset does not own it at all.
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
    // Reported off the window that stands, which is not always the one this rejection proposed.
    log.debug(
        "[TTS cloud] rate limited (429); {} for {}ms",
        standing.stated ? "the provider asked to be left alone" : "backing off prefetch",
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
    window.updateAndGet(current -> Window.clear(current.generation + 1));
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

  /**
   * One back-off state: when the window ends, whether the provider named that moment itself, how
   * many rejections have run without a clean call, and the generation those rejections belong to.
   */
  private static final class Window {

    private final long endNanos;
    private final boolean stated;
    private final boolean open;
    private final long generation;
    private final int consecutive;

    /** No window at all, which no deadline can be mistaken for. */
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

    /**
     * This state after a 429, or this state untouched when the rejection belongs to a generation
     * the back-off has since left behind, which means a call succeeded (or the credentials changed)
     * while it was in flight.
     */
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

    /** Subtraction rather than {@code <}, so a wrapped monotonic clock still compares correctly. */
    boolean isOpen(long nowNanos) {
      return open && nowNanos - endNanos < 0;
    }

    /** How much of this window is left, for a log line that reports what actually stands. */
    long remainingMillis(long nowNanos) {
      return isOpen(nowNanos) ? (endNanos - nowNanos) / NANOS_PER_MILLI : 0;
    }

    /**
     * Whether this window should stand rather than a newly opened one. A statement outranks a guess
     * in either direction, however the two deadlines compare: a shorter stated wait is still the
     * provider naming the moment it will serve again, and a longer guess is still a guess. Only two
     * windows of the same kind are compared by deadline, where the later one wins.
     */
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
