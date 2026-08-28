package com.grahambartley.runelite.voiced.dialogue.synthesis;

/**
 * Converts Google AI Studio's reported token counts into an estimated cost, and is the one place
 * those rates live.
 *
 * <p>Only AI Studio needs this. OpenRouter states what a key has actually spent through {@code
 * /api/v1/key}, and because the translation hop bills against the same key its cost is already
 * inside that figure, so nothing on the OpenRouter path passes through here. The Gemini API returns
 * no cost of any kind, and its real billing sits behind the Cloud Billing API (a separate
 * credential and cloud project, out of reach for a plugin), so the closest honest figure is the
 * provider's own metered token counts multiplied by its published rate.
 *
 * <p>That leaves exactly one modelled input: the rate. The quantities are measured, taken from the
 * {@code usageMetadata} the API returns per call, so this is not a guess at how long a line is or
 * how many tokens a character becomes. The readout labels the resulting figure an estimate anyway,
 * because a published rate is not a receipt.
 *
 * <p>A session is billed for up to two different models, so it is priced against both. Speech runs
 * on the TTS model, where audio output dominates everything else. A non-English language or a
 * speaking style adds a translation hop on a Flash Lite class model, which is text in and text out
 * at a small fraction of the speech rate; cheap is not free, so it is costed rather than ignored.
 *
 * <p>Rates come from Google's Gemini API pricing page (https://ai.google.dev/pricing).
 */
public final class SpendPricing {

  /** TTS audio output, $10.00 per million tokens. */
  static final double AUDIO_USD_PER_TOKEN = 10.00 / 1_000_000;

  /** TTS text input, $0.50 per million tokens. */
  static final double SPEECH_TEXT_USD_PER_TOKEN = 0.50 / 1_000_000;

  /** Flash Lite text input, $0.10 per million tokens. */
  static final double TRANSLATION_INPUT_USD_PER_TOKEN = 0.10 / 1_000_000;

  /** Flash Lite text output, $0.40 per million tokens. */
  static final double TRANSLATION_OUTPUT_USD_PER_TOKEN = 0.40 / 1_000_000;

  private SpendPricing() {}

  /**
   * Estimated USD for a session's speech calls. Audio output dominates by twenty to one, so a
   * line's cost tracks how long it is to speak far more than how long it is to read.
   */
  public static double estimateSpeechUsd(long audioTokens, long promptTokens) {
    return Math.max(0, audioTokens) * AUDIO_USD_PER_TOKEN
        + Math.max(0, promptTokens) * SPEECH_TEXT_USD_PER_TOKEN;
  }

  /** Estimated USD for a session's translation hops, priced against the Flash Lite rates. */
  public static double estimateTranslationUsd(long inputTokens, long outputTokens) {
    return Math.max(0, inputTokens) * TRANSLATION_INPUT_USD_PER_TOKEN
        + Math.max(0, outputTokens) * TRANSLATION_OUTPUT_USD_PER_TOKEN;
  }
}
