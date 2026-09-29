package com.grahambartley.runelite.voiced.dialogue.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import org.junit.Test;

public class CloudCacheKeyBuilderTest {

  private static String build(
      String voice, int speedPercent, CharacterProfile profile, String languageToken) {
    return CloudCacheKeyBuilder.build(voice, speedPercent, profile, languageToken);
  }

  @Test
  public void baseKeyIsVoiceAndProfileWithNoOptionalFragments() {
    assertEquals(
        "every speaker resolves to a profile, so the profile fragment is part of the base key",
        "v|p" + TestFixtures.TROLL_PROFILE.cacheKey(),
        build("v", 100, TestFixtures.TROLL_PROFILE, null));
  }

  @Test
  public void speedFragmentOnlyWhenNonDefault() {
    assertFalse(build("v", 100, TestFixtures.TROLL_PROFILE, null).contains("|s"));
    assertTrue(build("v", 150, TestFixtures.TROLL_PROFILE, null).contains("|s150"));
  }

  @Test
  public void twoProfilesNeverShareAKey() {
    assertNotEquals(
        build("v", 100, TestFixtures.TROLL_PROFILE, null),
        build("v", 100, TestFixtures.NARRATOR_PROFILE, null));
  }

  @Test
  public void languageFragmentOnlyWhenThereIsALanguageToken() {
    assertTrue(build("v", 100, TestFixtures.TROLL_PROFILE, "FRENCH").endsWith("|lFRENCH"));
    assertFalse(build("v", 100, TestFixtures.TROLL_PROFILE, null).contains("|l"));
  }

  @Test
  public void theModelIsNotPartOfTheKey() {
    assertFalse(build("v", 100, TestFixtures.TROLL_PROFILE, null).contains("gemini"));
  }

  @Test
  public void fragmentsAreAppendedInVoiceSpeedProfileLanguageOrder() {
    assertEquals(
        "v|s150|p" + TestFixtures.TROLL_PROFILE.cacheKey() + "|lFRENCH+PIRATE",
        build("v", 150, TestFixtures.TROLL_PROFILE, "FRENCH+PIRATE"));
  }

  @Test
  public void theKeyForAFixedLineNeverChangesAcrossReleases() {
    CharacterProfile bartender =
        new CharacterProfile(
            "Bartender",
            "Strong London English accent, British English pronunciation",
            "Plain, friendly publican",
            "Steady and conversational.");
    assertEquals(
        "changing this key re-bills every cached line for every user",
        "en-gb-tutor-9|pb0e3b89d",
        build("en-gb-tutor-9", 100, bartender, null));
  }
}
