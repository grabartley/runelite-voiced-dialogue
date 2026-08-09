package com.grahambartley.synthesis;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks the cloud backend's rate-limit (429) back-off. A 429 opens a window that grows
 * geometrically per consecutive hit and is capped so prefetch never retry-storms; a clean call
 * clears it so prefetch resumes immediately.
 *
 * <p>Both updates are synchronized because the live synthesis pool and the prefetch pool report
 * into the same instance: an unsynchronized clear could otherwise read a stale window and erase a
 * 429 that another worker had just recorded, leaving prefetch free to pile onto the limit.
 */
@Slf4j
final class RateLimitBackoff {

  /** Base 429 back-off; doubled per consecutive limit hit and capped so prefetch never storms. */
  private static final long BACKOFF_BASE_MILLIS = 1_000;

  private static final long BACKOFF_MAX_MILLIS = 30_000;

  /** Epoch-millis until which the backend is backing off; 0 means not throttled. */
  private final AtomicLong backoffUntil = new AtomicLong();

  /** Consecutive 429s, so the window grows geometrically and resets on a clean call. */
  private final AtomicInteger consecutive429 = new AtomicInteger();

  boolean isThrottled() {
    return System.currentTimeMillis() < backoffUntil.get();
  }

  /** Opens (or widens) the back-off window after a 429, geometrically per repeat hit. */
  synchronized void recordRateLimited() {
    long window = backoffWindowMillis(consecutive429.incrementAndGet());
    backoffUntil.set(System.currentTimeMillis() + window);
    log.debug("[TTS cloud] rate limited (429); backing off prefetch for {}ms", window);
  }

  /** Clears the back-off after a clean call so prefetch resumes immediately. */
  synchronized void recordSuccess() {
    consecutive429.set(0);
    backoffUntil.set(0);
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
}
