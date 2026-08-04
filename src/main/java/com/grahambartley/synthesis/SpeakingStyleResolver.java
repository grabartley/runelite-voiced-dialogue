package com.grahambartley.synthesis;

import com.grahambartley.VoicedDialogueConfig;
import java.util.ArrayList;
import java.util.List;

/** Resolves the configured style, including a deterministic per-line choice for Random mode. */
final class SpeakingStyleResolver {

  private static final VoicedDialogueConfig.SpeakingStyle[] RANDOM_POOL = buildRandomPool();

  private SpeakingStyleResolver() {}

  static VoicedDialogueConfig.SpeakingStyle resolve(
      VoicedDialogueConfig.SpeakingStyle configured, SynthesisRequest request) {
    if (configured == null || configured != VoicedDialogueConfig.SpeakingStyle.RANDOM) {
      return configured == null ? VoicedDialogueConfig.SpeakingStyle.NONE : configured;
    }
    int hash = 17;
    hash = 31 * hash + (request.text() == null ? 0 : request.text().hashCode());
    hash = 31 * hash + (request.voice() == null ? 0 : request.voice().key().hashCode());
    hash = 31 * hash + Boolean.hashCode(request.player());
    return RANDOM_POOL[Math.floorMod(hash, RANDOM_POOL.length)];
  }

  private static VoicedDialogueConfig.SpeakingStyle[] buildRandomPool() {
    List<VoicedDialogueConfig.SpeakingStyle> styles = new ArrayList<>();
    for (VoicedDialogueConfig.SpeakingStyle style : VoicedDialogueConfig.SpeakingStyle.values()) {
      if (style != VoicedDialogueConfig.SpeakingStyle.NONE
          && style != VoicedDialogueConfig.SpeakingStyle.RANDOM) {
        styles.add(style);
      }
    }
    return styles.toArray(new VoicedDialogueConfig.SpeakingStyle[0]);
  }
}
