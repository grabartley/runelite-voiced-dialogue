package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;

final class CloudCacheKeyBuilder {

  private CloudCacheKeyBuilder() {}

  static String build(
      String voice, int speedPercent, CharacterProfile profile, String languageToken) {
    StringBuilder variant = new StringBuilder(voice);
    if (speedPercent != CloudBackendSupport.DEFAULT_SPEED_PERCENT) {
      variant.append("|s").append(speedPercent);
    }
    variant.append("|p").append(profile.cacheKey());
    if (languageToken != null) {
      variant.append("|l").append(languageToken);
    }
    return variant.toString();
  }
}
