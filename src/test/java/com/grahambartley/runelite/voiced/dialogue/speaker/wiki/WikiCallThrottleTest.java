package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class WikiCallThrottleTest {

  @Test
  public void theFirstTurnIsTakenImmediately() {
    long before = System.nanoTime();
    new WikiCallThrottle(60_000).awaitTurn();

    assertTrue(
        "the first call never waits", System.nanoTime() - before < TimeUnit.SECONDS.toNanos(30));
  }

  @Test
  public void aSecondTurnWaitsOutTheInterval() {
    long interval = 20;
    WikiCallThrottle throttle = new WikiCallThrottle(interval);

    long before = System.nanoTime();
    throttle.awaitTurn();
    throttle.awaitTurn();

    assertTrue(
        "a second call waits at least the interval",
        System.nanoTime() - before >= TimeUnit.MILLISECONDS.toNanos(interval));
  }

  @Test
  public void aZeroIntervalNeverWaits() {
    WikiCallThrottle throttle = new WikiCallThrottle(0);
    long before = System.nanoTime();
    for (int call = 0; call < 5; call++) {
      throttle.awaitTurn();
    }

    assertTrue(
        "a zero interval never waits", System.nanoTime() - before < TimeUnit.SECONDS.toNanos(30));
  }

  @Test
  public void aWaitingTurnIgnoresAnInterruptAndStillTakesItsTurn() throws Exception {
    WikiCallThrottle throttle = new WikiCallThrottle(200);
    throttle.awaitTurn();

    AtomicBoolean tookTurn = new AtomicBoolean();
    CountDownLatch done = new CountDownLatch(1);
    Thread waiter =
        new Thread(
            () -> {
              throttle.awaitTurn();
              tookTurn.set(true);
              done.countDown();
            });

    waiter.start();
    waiter.interrupt();

    assertTrue("the waiter completes its turn", done.await(5, TimeUnit.SECONDS));
    assertTrue("an interrupt never makes a waiting call skip the wiki", tookTurn.get());
  }
}
