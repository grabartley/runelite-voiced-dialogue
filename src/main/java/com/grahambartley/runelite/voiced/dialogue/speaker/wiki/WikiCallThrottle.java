package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import java.util.concurrent.TimeUnit;

public final class WikiCallThrottle {

  private final long intervalNanos;

  private boolean started;

  private long lastCallAt;

  public WikiCallThrottle(long intervalMillis) {
    this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis);
  }

  public boolean awaitTurn() {
    long waitNanos;
    synchronized (this) {
      long now = System.nanoTime();
      long turnAt = started ? lastCallAt + intervalNanos : now;
      if (turnAt < now) {
        turnAt = now;
      }
      waitNanos = turnAt - now;
      lastCallAt = turnAt;
      started = true;
    }
    if (waitNanos > 0) {
      try {
        TimeUnit.NANOSECONDS.sleep(waitNanos);
      } catch (InterruptedException interrupted) {
        return false;
      }
    }
    return true;
  }
}
