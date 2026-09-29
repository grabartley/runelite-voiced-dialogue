package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;

final class CloudCacheKeyBuilder {

  private CloudCacheKeyBuilder() {}

  static String build(
      String voice,
      VoiceSpec speaker,
      int speedPercent,
      CharacterProfile profile,
      String languageToken) {
    StringBuilder variant = new StringBuilder(voice).append("|a").append(speakerToken(speaker));
    if (speedPercent != CloudBackendSupport.DEFAULT_SPEED_PERCENT) {
      variant.append("|s").append(speedPercent);
    }
    variant.append("|p").append(profile.cacheKey());
    if (languageToken != null) {
      variant.append("|l").append(languageToken);
    }
    return variant.toString();
  }

  static String speakerToken(VoiceSpec speaker) {
    if (speaker.narrator()) {
      return "NARRATOR";
    }
    return speaker.child() ? "CHILD_" + speaker.gender().name() : speaker.gender().name();
  }
}
