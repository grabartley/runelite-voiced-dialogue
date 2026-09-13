package com.grahambartley.runelite.voiced.dialogue.speaker;

public final class WikiCallThrottle {

  private final long intervalMillis;

  private long lastCallAt;

  public WikiCallThrottle(long intervalMillis) {
    this.intervalMillis = intervalMillis;
  }

  public synchronized void awaitTurn() {
    long now = System.currentTimeMillis();
    long earliest = lastCallAt + intervalMillis;
    if (lastCallAt > 0 && now < earliest) {
      try {
        Thread.sleep(earliest - now);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      now = System.currentTimeMillis();
    }
    lastCallAt = now;
  }
}
