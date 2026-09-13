package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import java.util.concurrent.TimeUnit;

public final class WikiCallThrottle {

  private final long intervalNanos;

  private long lastCallAt;

  public WikiCallThrottle(long intervalMillis) {
    this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis);
  }

  public boolean awaitTurn() {
    long waitNanos;
    synchronized (this) {
      long now = System.nanoTime();
      long turnAt = lastCallAt == 0 ? now : lastCallAt + intervalNanos;
      if (turnAt < now) {
        turnAt = now;
      }
      waitNanos = turnAt - now;
      lastCallAt = turnAt == 0 ? 1 : turnAt;
    }
    if (waitNanos > 0) {
      try {
        TimeUnit.NANOSECONDS.sleep(waitNanos);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return true;
  }
}
