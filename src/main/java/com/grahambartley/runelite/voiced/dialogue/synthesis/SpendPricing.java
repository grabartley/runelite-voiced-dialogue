package com.grahambartley.runelite.voiced.dialogue.synthesis;

/**
 * Converts Google AI Studio's reported token counts into an estimated cost, and is the one place
 * those rates live.
 *
 * <p>Only AI Studio needs this. OpenRouter states what a key has actually spent through {@code
 * /api/v1/key}, so its readout quotes a billed figure and never passes through here. The Gemini API
 * returns no cost of any kind, and its real billing sits behind the Cloud Billing API (a separate
 * credential and cloud project, out of reach for a plugin), so the closest honest figure is the
 * provider's own metered token counts multiplied by its published rate.
 *
 * <p>That leaves exactly one modelled input: the rate. The quantities are measured, taken from the
 * {@code usageMetadata} the API returns per call, so this is not a guess at how long a line is or
 * how many tokens a character becomes. The readout labels the resulting figure an estimate anyway,
 * because a published rate is not a receipt.
 *
 * <p>Rates come from Google's Gemini API pricing page (https://ai.google.dev/pricing) for the TTS
 * model: audio output billed per million output tokens, text input per million input tokens.
 */
public final class SpendPricing {

  /** Audio output, $10.00 per million tokens. */
  static final double AUDIO_USD_PER_TOKEN = 10.00 / 1_000_000;

  /** Text input, $0.50 per million tokens. */
  static final double TEXT_USD_PER_TOKEN = 0.50 / 1_000_000;

  private SpendPricing() {}

  /**
   * Estimated USD for a set of metered tokens. Audio output dominates by twenty to one, so a line's
   * cost tracks how long it is to speak far more than how long it is to read.
   */
  public static double estimateUsd(long audioTokens, long textTokens) {
    return Math.max(0, audioTokens) * AUDIO_USD_PER_TOKEN
        + Math.max(0, textTokens) * TEXT_USD_PER_TOKEN;
  }
}
