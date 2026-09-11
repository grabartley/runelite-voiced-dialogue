package com.grahambartley.runelite.voiced.dialogue.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import org.junit.Test;

public class CloudCacheKeyBuilderTest {

  private static String build(
      String modelId,
      String voice,
      int speedPercent,
      CharacterProfile profile,
      String language,
      boolean skipTranslation) {
    return CloudCacheKeyBuilder.build(
        modelId, voice, speedPercent, profile, language, skipTranslation);
  }

  @Test
  public void baseKeyIsModelVoiceAndProfileWithNoOptionalFragments() {
    assertEquals(
        "every speaker resolves to a profile, so the profile fragment is part of the base key",
        "m|v|p" + TestFixtures.TROLL_PROFILE.cacheKey(),
        build("m", "v", 100, TestFixtures.TROLL_PROFILE, "English", false));
  }

  @Test
  public void speedFragmentOnlyWhenNonDefault() {
    assertFalse(build("m", "v", 100, TestFixtures.TROLL_PROFILE, "English", false).contains("|s"));
    assertTrue(
        build("m", "v", 150, TestFixtures.TROLL_PROFILE, "English", false).contains("|s150"));
  }

  @Test
  public void twoProfilesNeverShareAKey() {
    assertNotEquals(
        build("m", "v", 100, TestFixtures.TROLL_PROFILE, "English", false),
        build("m", "v", 100, TestFixtures.NARRATOR_PROFILE, "English", false));
  }

  @Test
  public void languageFragmentOnlyWhenTheLineIsActuallyTranslated() {
    assertTrue(
        "a translated line folds the lowercased language in",
        build("m", "v", 100, TestFixtures.TROLL_PROFILE, "French", false).contains("|lfrench"));
    assertFalse(
        "a skip-translation line keeps the plain pre-translation key",
        build("m", "v", 100, TestFixtures.TROLL_PROFILE, "French", true).contains("|l"));
    assertFalse(
        "plain English adds no language fragment",
        build("m", "v", 100, TestFixtures.TROLL_PROFILE, "English", false).contains("|l"));
  }

  @Test
  public void fragmentsAreAppendedInModelVoiceSpeedProfileLanguageOrder() {
    assertEquals(
        "m|v|s150|p" + TestFixtures.TROLL_PROFILE.cacheKey() + "|lfrench",
        build("m", "v", 150, TestFixtures.TROLL_PROFILE, "French", false));
  }
}
