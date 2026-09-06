package com.grahambartley.runelite.voiced.dialogue.speech;

/**
 * Turns two readings of an OpenRouter key's all-time credit usage into what this session spent.
 *
 * <p>The baseline is taken once the key can first be read, and the spend is the rise above it. That
 * makes the figure OpenRouter's own, not the plugin's: whatever the provider decided to charge for
 * a line, including anything the plugin cannot see, is inside the delta.
 *
 * <p>Two consequences are deliberate. The meter reports {@code null} rather than zero until it has
 * a baseline, so an unread balance is never mistaken for a free session. And a key change resets
 * the baseline, since usage on a different key is a different running total and subtracting across
 * the two would be meaningless.
 *
 * <p>Any usage on the key from outside this plugin lands in the delta too; it is the key's spend
 * for the session, which is the number the account is actually billed.
 */
public final class OpenRouterCreditMeter {

  private Double baseline;

  /** The key whose baseline is held, so a swapped key restarts the measurement. */
  private String baselineKey;

  /**
   * Records the session's starting point for {@code apiKey}. Ignored when the reading is absent or
   * a baseline for that same key already stands, so a re-warm mid-session never rebases and quietly
   * erases spend already made.
   */
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

  /**
   * What the session has spent given a fresh reading, or {@code null} when no baseline stands for
   * this key. Clamped at zero: a usage figure below the baseline means the counter moved under us
   * (a key swap, or a provider-side correction), and a negative spend would be nonsense.
   */
  public synchronized Double spentSince(String apiKey, Double currentUsage) {
    if (baseline == null || currentUsage == null || !sameKey(apiKey)) {
      return null;
    }
    return Math.max(0, currentUsage - baseline);
  }

  /** Whether a baseline is held for {@code apiKey}, so a caller can skip a pointless read. */
  public synchronized boolean hasBaselineFor(String apiKey) {
    return baseline != null && sameKey(apiKey);
  }

  /** Drops the baseline, so the next reading starts a fresh session measurement. */
  public synchronized void reset() {
    baseline = null;
    baselineKey = null;
  }

  private boolean sameKey(String apiKey) {
    String trimmed = apiKey == null ? null : apiKey.trim();
    return baselineKey == null ? trimmed == null : baselineKey.equals(trimmed);
  }
}
