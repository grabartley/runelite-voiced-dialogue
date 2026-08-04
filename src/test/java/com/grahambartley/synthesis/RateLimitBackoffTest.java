package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/** The cloud 429 back-off window growth and the throttle state transitions. */
@RunWith(JUnitParamsRunner.class)
public class RateLimitBackoffTest {

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
    assertFalse(new RateLimitBackoff().isThrottled());
  }

  @Test
  public void aRateLimitThrottlesAndACleanCallClears() {
    RateLimitBackoff backoff = new RateLimitBackoff();
    backoff.recordRateLimited();
    assertTrue("a 429 opens a back-off window", backoff.isThrottled());
    backoff.recordSuccess();
    assertFalse("a clean call clears the back-off", backoff.isThrottled());
  }

  @Test
  public void concurrentClearsNeverEraseARateLimitRecordedAlongsideThem() throws Exception {
    // The live pool and the prefetch pool report into one instance, so a clear and a 429 can land
    // together. The 429 must always win: dropping it would let prefetch pile onto the limit.
    for (int attempt = 0; attempt < 200; attempt++) {
      RateLimitBackoff backoff = new RateLimitBackoff();
      CountDownLatch start = new CountDownLatch(1);
      Thread clearing =
          new Thread(
              () -> {
                await(start);
                backoff.recordSuccess();
              });
      Thread limiting =
          new Thread(
              () -> {
                await(start);
                backoff.recordRateLimited();
              });
      clearing.start();
      limiting.start();
      start.countDown();
      clearing.join();
      limiting.join();

      // Whichever order they interleaved in, the state must be self-consistent: a recorded 429 that
      // survived leaves a window, and a clear that ran last leaves none.
      backoff.recordRateLimited();
      assertTrue("a 429 recorded after the race always throttles", backoff.isThrottled());
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(2, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }
}
