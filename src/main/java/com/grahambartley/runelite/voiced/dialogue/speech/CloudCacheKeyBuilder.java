package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;

/**
 * Builds the cache-key variant for a cloud line: everything outside the (voice, emotion, original
 * text) base key that changes the rendered audio, so a cache hit always returns the bytes the
 * current settings would synthesize. Each fragment is appended only when it actually applies, so a
 * line keeps the simplest key it can and existing cache entries stay valid when an unrelated
 * setting changes.
 *
 * <ul>
 *   <li>model + voice: the base, so a future model switch or a different voice never replays the
 *       wrong audio;
 *   <li>{@code |s}: speed, only when non-default;
 *   <li>{@code |p}: character-profile content digest, only when a profile is present;
 *   <li>{@code |l}: target language/style, only when the line is actually translated. A
 *       skip-translation request is voiced verbatim, so it keeps the plain pre-translation key and
 *       never collides with a translated line of the same text.
 * </ul>
 */
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
    if (profile != null) {
      variant.append("|p").append(profile.cacheKey());
    }
    if (CloudTtsText.needsTranslation(language) && !skipTranslation) {
      variant.append("|l").append(language.toLowerCase());
    }
    return variant.toString();
  }
}
