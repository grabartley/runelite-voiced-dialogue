package com.grahambartley.runelite.voiced.dialogue.speech.spend;

public final class SpendPricing {

  static final double AUDIO_USD_PER_TOKEN = 10.00 / 1_000_000;

  static final double SPEECH_TEXT_USD_PER_TOKEN = 0.50 / 1_000_000;

  static final double TRANSLATION_INPUT_USD_PER_TOKEN = 0.10 / 1_000_000;

  static final double TRANSLATION_OUTPUT_USD_PER_TOKEN = 0.40 / 1_000_000;

  private SpendPricing() {}

  public static double estimateSpeechUsd(long audioTokens, long promptTokens) {
    return Math.max(0, audioTokens) * AUDIO_USD_PER_TOKEN
        + Math.max(0, promptTokens) * SPEECH_TEXT_USD_PER_TOKEN;
  }

  public static double estimateTranslationUsd(long inputTokens, long outputTokens) {
    return Math.max(0, inputTokens) * TRANSLATION_INPUT_USD_PER_TOKEN
        + Math.max(0, outputTokens) * TRANSLATION_OUTPUT_USD_PER_TOKEN;
  }
}
