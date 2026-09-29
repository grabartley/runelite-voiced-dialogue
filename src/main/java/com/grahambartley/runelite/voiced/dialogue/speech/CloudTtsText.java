package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;

public final class CloudTtsText {

  static final String DEFAULT_LANGUAGE = "English";

  private CloudTtsText() {}

  static boolean needsTranslation(String language) {
    return language != null
        && !language.trim().isEmpty()
        && !language.trim().equalsIgnoreCase(DEFAULT_LANGUAGE);
  }

  static String effectiveSpokenLanguage(VoicedDialogueConfig config, SynthesisRequest request) {
    return combineLanguage(config.cloudLanguage().label(), styleFor(config, request));
  }

  static String languageCacheToken(VoicedDialogueConfig config, SynthesisRequest request) {
    if (request.skipTranslation() || !needsTranslation(effectiveSpokenLanguage(config, request))) {
      return null;
    }
    VoicedDialogueConfig.SpeakingStyle style = styleFor(config, request);
    String language = config.cloudLanguage().name();
    return style.isNone() ? language : language + "+" + style.name();
  }

  private static VoicedDialogueConfig.SpeakingStyle styleFor(
      VoicedDialogueConfig config, SynthesisRequest request) {
    if (request.voice().narrator()) {
      return VoicedDialogueConfig.SpeakingStyle.NONE;
    }
    return request.player() ? config.cloudPlayerSpeakingStyle() : config.cloudNpcSpeakingStyle();
  }

  static String spokenLanguage(VoicedDialogueConfig config) {
    return baseLanguage(config.cloudLanguage().label());
  }

  private static String baseLanguage(String language) {
    return language == null || language.trim().isEmpty() ? DEFAULT_LANGUAGE : language.trim();
  }

  static String combineLanguage(String language, VoicedDialogueConfig.SpeakingStyle quirk) {
    String base = baseLanguage(language);
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
