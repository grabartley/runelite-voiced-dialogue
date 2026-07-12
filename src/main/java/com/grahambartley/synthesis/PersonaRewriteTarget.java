package com.grahambartley.synthesis;

import com.grahambartley.VoicedDialogueConfig;

/** Adds an opt-in creative player-persona rewrite to the existing translation/style target. */
final class PersonaRewriteTarget {

  private PersonaRewriteTarget() {}

  static String apply(
      VoicedDialogueConfig config, SynthesisRequest request, String targetLanguageAndStyle) {
    if (!enabled(config, request)) {
      return targetLanguageAndStyle;
    }
    return targetLanguageAndStyle
        + ", creatively rewritten in character using this persona: "
        + request.profile().style().trim()
        + ". Brief in-character ad-libs, silly remarks, and strong profanity explicitly requested"
        + " by the persona are allowed; preserve the original meaning, names, and RuneScape terms";
  }

  static boolean enabled(VoicedDialogueConfig config, SynthesisRequest request) {
    return config.allowMaturePersonaAdlibs()
        && request.player()
        && !request.skipTranslation()
        && request.profile() != null
        && request.profile().style() != null
        && !request.profile().style().trim().isEmpty();
  }
}
