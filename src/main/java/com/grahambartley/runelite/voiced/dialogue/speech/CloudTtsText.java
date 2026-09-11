package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;

public final class CloudTtsText {

  private CloudTtsText() {}

  static boolean needsTranslation(String language) {
    return language != null
        && !language.trim().isEmpty()
        && !language.trim().equalsIgnoreCase("English");
  }

  static String effectiveSpokenLanguage(VoicedDialogueConfig config, SynthesisRequest request) {
    return combineLanguage(config.cloudLanguage().label(), styleFor(config, request));
  }

  private static VoicedDialogueConfig.SpeakingStyle styleFor(
      VoicedDialogueConfig config, SynthesisRequest request) {
    if (request.voice().narrator()) {
      return VoicedDialogueConfig.SpeakingStyle.NONE;
    }
    return request.player() ? config.cloudPlayerSpeakingStyle() : config.cloudNpcSpeakingStyle();
  }

  static String combineLanguage(String language, VoicedDialogueConfig.SpeakingStyle quirk) {
    String base = language == null || language.trim().isEmpty() ? "English" : language.trim();
    if (quirk == null || quirk.isNone()) {
      return base;
    }
    return base + " " + quirk.phrase();
  }

  public static String translatorSystemPrompt(String language) {
    return "You are a translation engine for an Old School RuneScape dialogue voice plugin."
        + " Translate the user's line into "
        + language
        + ". Preserve proper nouns, character names, place names, item names, and RuneScape"
        + " terminology exactly as written. Output only the translation, with no quotes, notes,"
        + " explanations, or preamble.";
  }
}
