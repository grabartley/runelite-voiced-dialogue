package com.grahambartley.runelite.voiced.dialogue.speech.model;

/**
 * The plugin's fixed translation model, Gemini 3.1 Flash Lite: the lightweight hop that renders a
 * line into the configured spoken language before synthesis, fast and cheap relative to the speech
 * call itself.
 *
 * <p>Both providers serve the same model, so the id lives here rather than on either transport.
 * {@link GeminiTtsModel} pins the speech model the same way, and for the same reason: a model bump
 * cannot half-apply across the two providers.
 */
public final class GeminiTranslationModel {

  /** The bare Gemini API model name, used by the direct Google AI Studio hop. */
  public static final String GEMINI_MODEL_ID = "gemini-3.1-flash-lite";

  /** The same model under OpenRouter's {@code google/} namespace. */
  public static final String MODEL_ID = "google/" + GEMINI_MODEL_ID;

  private GeminiTranslationModel() {}
}
