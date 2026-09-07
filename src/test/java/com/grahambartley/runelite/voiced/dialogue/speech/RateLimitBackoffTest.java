package com.grahambartley.runelite.voiced.dialogue.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/** The cloud 429 back-off window growth, the honoured wait, and the throttle state transitions. */
@RunWith(JUnitParamsRunner.class)
public class RateLimitBackoffTest {

  /** A rejection that stated no wait, leaving the window to the computed ladder. */
  private static final long NO_STATED_WAIT = 0;

  /** The wait a real per-model daily cap asked for. */
  private static final long DAILY_CAP_WAIT_MILLIS = 2_917_000;

  private static final long ONE_HOUR_MILLIS = 60 * 60 * 1_000L;

  /** The injected monotonic clock reads nanoseconds, as {@link System#nanoTime} does. */
  private final AtomicLong nanos = new AtomicLong(-4_000_000_000L);

  private final RateLimitBackoff backoff = new RateLimitBackoff(nanos::get);

  @Test
  @Parameters({
    "1, 1000",
    "2, 2000",
    "3, 4000",
    "50, 30000",
  })
  public void windowGrowsGeometricallyAndCaps(int attempt, int expectedWindow) {
    assertEquals(expectedWindow, RateLimitBackoff.backoffWindowMillis(attempt));
  }

  @Test
  public void freshBackoffIsNotThrottled() {
    assertFalse(backoff.isThrottled());
    assertFalse(backoff.isRefusing());
  }

  @Test
  public void aRateLimitThrottlesAndACleanCallClears() {
    rateLimited(NO_STATED_WAIT);
    assertTrue("a 429 opens a back-off window", backoff.isThrottled());
    backoff.recordSuccess();
    assertFalse("a clean call clears the back-off", backoff.isThrottled());
  }

  @Test
  public void anUnstatedWaitKeepsTheLadderExactly() {
    rateLimited(NO_STATED_WAIT);
    elapse(999);
    assertTrue("the first hit pauses for the base window", backoff.isThrottled());
    elapse(2);
    assertFalse(backoff.isThrottled());

    rateLimited(NO_STATED_WAIT);
    elapse(1_999);
    assertTrue("the second doubles it", backoff.isThrottled());
    elapse(2);
    assertFalse(backoff.isThrottled());
  }

  @Test
  public void anUnstatedWaitStandsSpeculationDownWithoutClosingTheBackend() {
    rateLimited(NO_STATED_WAIT);

    assertTrue("prefetch stands down on a computed window", backoff.isThrottled());
    assertFalse(
        "a computed window is a guess, so a real line is still worth attempting",
        backoff.isRefusing());
  }

  @Test
  public void aStatedWaitIsHonouredInFullRatherThanGuessedAt() {
    rateLimited(DAILY_CAP_WAIT_MILLIS);

    elapse(30_000);
    assertTrue(
        "the ladder's own ceiling is nowhere near what the provider asked for",
        backoff.isRefusing());

    elapse(DAILY_CAP_WAIT_MILLIS - 30_001);
    assertTrue("still inside the stated wait", backoff.isRefusing());
    elapse(2);
    assertFalse("the wait passing ends the refusal", backoff.isRefusing());
    assertFalse(backoff.isThrottled());
  }

  @Test
  public void anAbsurdStatedWaitIsClampedToTheCeiling() {
    rateLimited(Long.MAX_VALUE / 2);

    elapse(ONE_HOUR_MILLIS - 1);
    assertTrue(backoff.isRefusing());
    elapse(2);
    assertFalse("a nonsense wait must not mute the session outright", backoff.isRefusing());
  }

  @Test
  public void aCleanCallClearsAStatedWaitToo() {
    rateLimited(DAILY_CAP_WAIT_MILLIS);

    backoff.recordSuccess();

    assertFalse(backoff.isRefusing());
    assertFalse(backoff.isThrottled());
  }

  @Test
  public void aGuessLandingBehindAStatedWaitDoesNotShortenIt() {
    // A line and a prefetch are rejected on separate threads, so the two arrive interleaved.
    rateLimited(DAILY_CAP_WAIT_MILLIS);

    rateLimited(NO_STATED_WAIT);

    elapse(30_000);
    assertTrue("the stated wait outlives a ladder rung that landed after it", backoff.isRefusing());
    elapse(DAILY_CAP_WAIT_MILLIS - 30_001);
    assertTrue(backoff.isRefusing());
    elapse(2);
    assertFalse(backoff.isRefusing());
  }

  @Test
  public void aStatedWaitAfterAGuessTakesOverFromIt() {
    rateLimited(NO_STATED_WAIT);

    rateLimited(DAILY_CAP_WAIT_MILLIS);

    assertTrue("a statement beats the guess it lands behind", backoff.isRefusing());
  }

  @Test
  public void aStatedWaitShorterThanTheLiveGuessStillTakesOver() {
    // Five rejections put the ladder at 16s, then the provider names five.
    for (int i = 0; i < 5; i++) {
      rateLimited(NO_STATED_WAIT);
    }

    rateLimited(5_000);

    assertTrue("a shorter statement is still a statement", backoff.isRefusing());
    elapse(4_999);
    assertTrue(backoff.isRefusing());
    elapse(2);
    assertFalse("and it ends when the provider said it would", backoff.isRefusing());
    assertFalse("without the guess it displaced outliving it", backoff.isThrottled());
  }

  @Test
  public void aRejectionThatPredatesASuccessIsDropped() {
    // A line and a prefetch overlap, the prefetch is refused, the line succeeds, and the refusal's
    // handler is the one to finish second.
    long observed = backoff.generation();
    backoff.recordSuccess();

    backoff.recordRateLimited(DAILY_CAP_WAIT_MILLIS, observed);

    assertFalse("a call already proven to work is not undone", backoff.isRefusing());
    assertFalse(backoff.isThrottled());
  }

  @Test
  public void aShorterStatedWaitDoesNotShortenALongerOneAlreadyStanding() {
    // Two rejections state different waits; the nearer one is the second to commit.
    rateLimited(DAILY_CAP_WAIT_MILLIS);

    rateLimited(5_000);

    elapse(5_001);
    assertTrue("the further of two stated waits stands", backoff.isRefusing());
    elapse(DAILY_CAP_WAIT_MILLIS);
    assertFalse(backoff.isRefusing());
  }

  @Test
  public void aGuessOnceTheStatedWaitHasPassedOpensAPlainWindowAgain() {
    rateLimited(DAILY_CAP_WAIT_MILLIS);
    elapse(DAILY_CAP_WAIT_MILLIS + 1);

    rateLimited(NO_STATED_WAIT);

    assertFalse("a rejection stating nothing states nothing", backoff.isRefusing());
    assertTrue(backoff.isThrottled());
  }

  @Test
  public void changedCredentialsDropTheWindowAndTheLadderWithIt() {
    rateLimited(DAILY_CAP_WAIT_MILLIS);

    backoff.reset();

    assertFalse("the notice asked for this change, so it cannot be punished", backoff.isRefusing());
    assertFalse(backoff.isThrottled());

    rateLimited(NO_STATED_WAIT);
    elapse(999);
    assertTrue("and the ladder starts again from its base rung", backoff.isThrottled());
    elapse(2);
    assertFalse(backoff.isThrottled());
  }

  @Test
  public void aWindowOpenedBeforeAClockWrapStillCloses() {
    nanos.set(Long.MAX_VALUE - 1_000_000L);

    rateLimited(10);

    assertTrue("the deadline wrapped past the clock's own ceiling", backoff.isRefusing());
    elapse(11);
    assertFalse("a wrapped deadline is still reached, not held forever", backoff.isRefusing());
  }

  /** Records a rejection observed at the current generation, as a live caller does. */
  private void rateLimited(long statedWaitMillis) {
    backoff.recordRateLimited(statedWaitMillis, backoff.generation());
  }

  private void elapse(long millis) {
    nanos.addAndGet(millis * 1_000_000L);
  }
}
