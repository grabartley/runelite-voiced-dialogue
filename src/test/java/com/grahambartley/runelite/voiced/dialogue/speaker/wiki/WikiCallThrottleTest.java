package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class WikiCallThrottleTest {

  @Test
  public void theFirstTurnIsTakenImmediately() {
    WikiCallThrottle throttle = new WikiCallThrottle(60_000);

    long before = System.nanoTime();
    assertTrue(throttle.awaitTurn());
    assertTrue(
        "nothing waits on an unused throttle",
        System.nanoTime() - before < TimeUnit.SECONDS.toNanos(1));
  }

  @Test
  public void aSecondTurnWaitsOutTheInterval() {
    WikiCallThrottle throttle = new WikiCallThrottle(50);

    long before = System.nanoTime();
    assertTrue(throttle.awaitTurn());
    assertTrue(throttle.awaitTurn());

    assertTrue(
        "the interval is honoured between calls",
        System.nanoTime() - before >= TimeUnit.MILLISECONDS.toNanos(50));
  }

  @Test
  public void aZeroIntervalNeverWaits() {
    WikiCallThrottle throttle = new WikiCallThrottle(0);
    for (int call = 0; call < 5; call++) {
      assertTrue(throttle.awaitTurn());
    }
  }

  @Test
  public void anInterruptedWaitGivesUpItsTurn() throws Exception {
    WikiCallThrottle throttle = new WikiCallThrottle(60_000);
    throttle.awaitTurn();

    AtomicBoolean tookTurn = new AtomicBoolean(true);
    AtomicBoolean stayedInterrupted = new AtomicBoolean(false);
    CountDownLatch done = new CountDownLatch(1);
    Thread waiter =
        new Thread(
            () -> {
              tookTurn.set(throttle.awaitTurn());
              stayedInterrupted.set(Thread.currentThread().isInterrupted());
              done.countDown();
            });

    waiter.start();
    Thread.sleep(50);
    waiter.interrupt();

    assertTrue("the waiter gives up rather than hanging", done.await(5, TimeUnit.SECONDS));
    assertFalse("an interrupted call never reaches the wiki", tookTurn.get());
    assertTrue("the interrupt is left for the executor to see", stayedInterrupted.get());
  }
}
