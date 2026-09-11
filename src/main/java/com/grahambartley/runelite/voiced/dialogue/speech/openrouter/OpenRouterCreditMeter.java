package com.grahambartley.runelite.voiced.dialogue.speech.openrouter;

public final class OpenRouterCreditMeter {

  private Double baseline;

  private String baselineKey;

  public synchronized void recordBaseline(String apiKey, Double usage) {
    if (usage == null) {
      return;
    }
    if (baseline != null && sameKey(apiKey)) {
      return;
    }
    baseline = usage;
    baselineKey = apiKey == null ? null : apiKey.trim();
  }

  public synchronized Double spentSince(String apiKey, Double currentUsage) {
    if (baseline == null || currentUsage == null || !sameKey(apiKey)) {
      return null;
    }
    return Math.max(0, currentUsage - baseline);
  }

  public synchronized boolean hasBaselineFor(String apiKey) {
    return baseline != null && sameKey(apiKey);
  }

  public synchronized void reset() {
    baseline = null;
    baselineKey = null;
  }

  private boolean sameKey(String apiKey) {
    String trimmed = apiKey == null ? null : apiKey.trim();
    return baselineKey == null ? trimmed == null : baselineKey.equals(trimmed);
  }
}
