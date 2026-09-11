package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;

/**
 * Provider-neutral text rules shared by every cloud TTS provider: the translation-target decision,
 * the language/quirk combination, and the translator's fixed system prompt. Kept in one place so
 * every provider transforms a line identically, which keeps their synthesis caches and the
 * model-side prompt caches keyed the same way.
 */
public final class CloudTtsText {

  private CloudTtsText() {}

  /** A target language other than English (case-insensitive, blank treated as English). */
  static boolean needsTranslation(String language) {
    return language != null
        && !language.trim().isEmpty()
        && !language.trim().equalsIgnoreCase("English");
  }

  /**
   * The spoken language actually requested of the model for a line: the configured language with
   * the speaker-class Speaking Style appended, so "English" plus a Gen Z style becomes an "English
   * Gen Z slang" target that routes through the translation hop and is rewritten in that style. A
   * blank language defaults to English; the no-op style leaves the language untouched, so a class
   * set to None skips the hop while the other class can still be styled.
   */
  static String effectiveSpokenLanguage(VoicedDialogueConfig config, SynthesisRequest request) {
    return combineLanguage(config.cloudLanguage().label(), styleFor(config, request));
  }

  /**
   * The Speaking Style a line is rewritten in: the Player style for the player's own lines, the NPC
   * style for a character's, and none at all for narration, which is the game's own voice rather
   * than someone in the world putting on a register.
   */
  private static VoicedDialogueConfig.SpeakingStyle styleFor(
      VoicedDialogueConfig config, SynthesisRequest request) {
    if (request.voice().narrator()) {
      return VoicedDialogueConfig.SpeakingStyle.NONE;
    }
    return request.player() ? config.cloudPlayerSpeakingStyle() : config.cloudNpcSpeakingStyle();
  }

  /**
   * Appends a non-empty quirk phrase to the (blank-safe) base language, e.g. "French pirate speak".
   */
  static String combineLanguage(String language, VoicedDialogueConfig.SpeakingStyle quirk) {
    String base = language == null || language.trim().isEmpty() ? "English" : language.trim();
    if (quirk == null || quirk.isNone()) {
      return base;
    }
    return base + " " + quirk.phrase();
  }

  /**
   * The fixed translator system prompt for a target language, shared by every provider's
   * translation hop so they rewrite lines identically. The wording is deterministic in {@code
   * language} (no timestamps, names, or per-line text) so the same language always produces the
   * byte-identical prefix the model's prompt cache keys on.
   */
  public static String translatorSystemPrompt(String language) {
    return "You are a translation engine for an Old School RuneScape dialogue voice plugin."
        + " Translate the user's line into "
        + language
        + ". Preserve proper nouns, character names, place names, item names, and RuneScape"
        + " terminology exactly as written. Output only the translation, with no quotes, notes,"
        + " explanations, or preamble.";
  }
}
