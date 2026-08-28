package com.grahambartley.runelite.voiced.dialogue.synthesis;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig.TtsProvider;

/**
 * The one place the plugin's cost-per-character estimates live, so the {@code ::voicedspend}
 * readout and the README's average-line figure never drift apart.
 *
 * <p>Both providers voice through the same Gemini TTS model and translate through the same Gemini
 * Flash Lite model, and OpenRouter resells at the model's list price (its own fee is charged when
 * credits are bought, not per call), so the two currently carry identical rates. They are still
 * held per provider, since only one of them has to change its pricing for that to stop being true.
 *
 * <p>Rates are derived from the published per-token pricing, converted to characters:
 *
 * <ul>
 *   <li>Speech is billed on generated audio, which dominates the text input by orders of magnitude.
 *       At roughly two audio tokens per spoken character and $10 per million audio tokens, a
 *       character costs about $0.000025, which puts a typical 100-character line at $0.0025, the
 *       average quoted in the README.
 *   <li>Translation is text in, text out on a Flash Lite class model: about a quarter of a token
 *       per character each way at a blended ~$0.25 per million tokens, so roughly $0.00000013 per
 *       source character. It is a rounding error next to speech, and is reported separately so it
 *       reads as one.
 * </ul>
 *
 * <p>Pricing pages: Google AI Studio at https://ai.google.dev/pricing and OpenRouter at
 * https://openrouter.ai/models. Every figure here is an estimate; the provider's own dashboard is
 * the authority on what was actually billed.
 */
public final class SpendPricing {

  static final double OPENROUTER_SPEECH_USD_PER_CHARACTER = 0.000_025;

  static final double GOOGLE_AI_STUDIO_SPEECH_USD_PER_CHARACTER = 0.000_025;

  static final double OPENROUTER_TRANSLATION_USD_PER_CHARACTER = 0.000_000_13;

  static final double GOOGLE_AI_STUDIO_TRANSLATION_USD_PER_CHARACTER = 0.000_000_13;

  private SpendPricing() {}

  /** Estimated USD spent on a provider's speech and translation characters this session. */
  public static double estimateUsd(
      TtsProvider provider, long speechCharacters, long translationCharacters) {
    return Math.max(0, speechCharacters) * speechUsdPerCharacter(provider)
        + Math.max(0, translationCharacters) * translationUsdPerCharacter(provider);
  }

  static double speechUsdPerCharacter(TtsProvider provider) {
    return provider == TtsProvider.GOOGLE_AI_STUDIO
        ? GOOGLE_AI_STUDIO_SPEECH_USD_PER_CHARACTER
        : OPENROUTER_SPEECH_USD_PER_CHARACTER;
  }

  static double translationUsdPerCharacter(TtsProvider provider) {
    return provider == TtsProvider.GOOGLE_AI_STUDIO
        ? GOOGLE_AI_STUDIO_TRANSLATION_USD_PER_CHARACTER
        : OPENROUTER_TRANSLATION_USD_PER_CHARACTER;
  }
}
