package com.grahambartley.runelite.voiced.dialogue.speech;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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

  /** Epoch-millis until which the backend is backing off; 0 means not throttled. */
  private final AtomicLong backoffUntil = new AtomicLong();

  /** Whether the open window is the provider's own stated wait rather than a computed guess. */
  private final AtomicBoolean stated = new AtomicBoolean();

  /** Consecutive 429s, so the window grows geometrically and resets on a clean call. */
  private final AtomicInteger consecutive429 = new AtomicInteger();

  private final LongSupplier clock;

  public RateLimitBackoff() {
    this(System::currentTimeMillis);
  }

  /** Test seam: drives the window off a supplied clock, so a wait can be watched passing. */
  RateLimitBackoff(LongSupplier clock) {
    this.clock = clock;
  }

  boolean isThrottled() {
    return clock.getAsLong() < backoffUntil.get();
  }

  /**
   * Whether the provider has stated a wait that has not yet passed, which is the one case where a
   * call is known to fail before it is made.
   */
  boolean isRefusing() {
    return stated.get() && isThrottled();
  }

  /**
   * Opens (or widens) the back-off window after a 429. {@code statedWaitMillis} is what the
   * rejection itself asked for, or 0 when it asked for nothing, in which case the window grows
   * geometrically per repeat hit as before.
   */
  void recordRateLimited(long statedWaitMillis) {
    int consecutive = consecutive429.incrementAndGet();
    boolean honoured = statedWaitMillis > 0;
    long window = honoured ? clamp(statedWaitMillis) : backoffWindowMillis(consecutive);
    stated.set(honoured);
    backoffUntil.set(clock.getAsLong() + window);
    if (honoured) {
      log.debug(
          "[TTS cloud] rate limited (429); provider asked for {}ms, waiting {}ms",
          statedWaitMillis,
          window);
      return;
    }
    log.debug("[TTS cloud] rate limited (429); backing off prefetch for {}ms", window);
  }

  /** Clears the back-off after any clean call so prefetch resumes immediately. */
  void recordSuccess() {
    if (backoffUntil.get() != 0) {
      consecutive429.set(0);
      stated.set(false);
      backoffUntil.set(0);
    }
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
}
