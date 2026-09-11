package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;

final class CloudCacheKeyBuilder {

  private CloudCacheKeyBuilder() {}

  static String build(
      String modelId,
      String voice,
      int speedPercent,
      CharacterProfile profile,
      String language,
      boolean skipTranslation) {
    StringBuilder variant = new StringBuilder(modelId).append('|').append(voice);
    if (speedPercent != CloudBackendSupport.DEFAULT_SPEED_PERCENT) {
      variant.append("|s").append(speedPercent);
    }
    variant.append("|p").append(profile.cacheKey());
    if (CloudTtsText.needsTranslation(language) && !skipTranslation) {
      variant.append("|l").append(language.toLowerCase());
    }
    return variant.toString();
  }
}
