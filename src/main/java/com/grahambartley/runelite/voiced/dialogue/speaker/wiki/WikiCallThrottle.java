package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import java.util.concurrent.TimeUnit;

public final class WikiCallThrottle {

  private final long intervalNanos;

  private long lastCallAt;

  public WikiCallThrottle(long intervalMillis) {
    this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis);
  }

  public synchronized boolean awaitTurn() {
    long now = System.nanoTime();
    if (lastCallAt != 0) {
      long remaining = lastCallAt + intervalNanos - now;
      if (remaining > 0) {
        try {
          TimeUnit.NANOSECONDS.sleep(remaining);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return false;
        }
        now = System.nanoTime();
      }
    }
    lastCallAt = now == 0 ? 1 : now;
    return true;
  }
}
