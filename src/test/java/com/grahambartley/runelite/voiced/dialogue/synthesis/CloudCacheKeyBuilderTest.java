package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The cloud cache-key variant string: which fragments are appended, and in what order. */
public class CloudCacheKeyBuilderTest {

  private static String build(
      String modelId,
      String voice,
      int speedPercent,
      String text,
      int maxChars,
      CharacterProfile profile,
      String language,
      boolean skipTranslation) {
    return CloudCacheKeyBuilder.build(
        modelId, voice, speedPercent, text, maxChars, profile, language, skipTranslation);
  }

  @Test
  public void baseKeyIsModelAndVoiceWithNoFragments() {
    assertEquals("m|v", build("m", "v", 100, "a", 600, null, "English", false));
  }

  @Test
  public void speedFragmentOnlyWhenNonDefault() {
    assertFalse(build("m", "v", 100, "a", 0, null, "English", false).contains("|s"));
    assertTrue(build("m", "v", 150, "a", 0, null, "English", false).contains("|s150"));
  }

  @Test
  public void capFragmentOnlyWhenLineWouldTruncate() {
    assertFalse(
        "a line within the cap is not re-keyed",
        build("m", "v", 100, "ab", 3, null, "English", false).contains("|c"));
    assertTrue(
        "a line longer than the cap folds the cap in",
        build("m", "v", 100, "abcdef", 3, null, "English", false).contains("|c3"));
  }

  @Test
  public void profileFragmentIsTheProfileContentKey() {
    String withProfile = build("m", "v", 100, "a", 0, TestFixtures.TROLL_PROFILE, "English", false);
    assertEquals("m|v|p" + TestFixtures.TROLL_PROFILE.cacheKey(), withProfile);
  }

  @Test
  public void languageFragmentOnlyWhenTheLineIsActuallyTranslated() {
    assertTrue(
        "a translated line folds the lowercased language in",
        build("m", "v", 100, "a", 0, null, "French", false).contains("|lfrench"));
    assertFalse(
        "a skip-translation line keeps the plain pre-translation key",
        build("m", "v", 100, "a", 0, null, "French", true).contains("|l"));
    assertFalse(
        "plain English adds no language fragment",
        build("m", "v", 100, "a", 0, null, "English", false).contains("|l"));
  }

  @Test
  public void fragmentsAreAppendedInModelVoiceSpeedCapProfileLanguageOrder() {
    assertEquals(
        "m|v|s150|c3|p" + TestFixtures.TROLL_PROFILE.cacheKey() + "|lfrench",
        build("m", "v", 150, "abcdef", 3, TestFixtures.TROLL_PROFILE, "French", false));
  }
}
