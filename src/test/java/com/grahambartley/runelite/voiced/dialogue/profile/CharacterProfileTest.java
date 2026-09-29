package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class CharacterProfileTest {

  private static final CharacterProfile WIZARD =
      new CharacterProfile(
          "Wizard", "Distinguished elderly British accent", "Warm and knowing", "Measured pace");

  @Test
  public void cacheKeyIsStableForIdenticalFields() {
    CharacterProfile same =
        new CharacterProfile(
            "Wizard", "Distinguished elderly British accent", "Warm and knowing", "Measured pace");
    assertEquals("identical profiles share a cache key", WIZARD.cacheKey(), same.cacheKey());
  }

  @Test
  public void trailingWhitespaceIsStrippedFromEveryField() {
    CharacterProfile padded =
        new CharacterProfile(
            "Wizard  ",
            "Distinguished elderly British accent\n",
            "Warm and knowing \t",
            "Measured pace   ");
    assertEquals(WIZARD, padded);
    assertEquals(
        "padded and unpadded profiles share a cache key", WIZARD.cacheKey(), padded.cacheKey());
  }

  @Test
  @Parameters(method = "changedDeliveryFieldProfiles")
  public void cacheKeyChangesWhenAnyDeliveryFieldChanges(CharacterProfile changed) {
    assertNotEquals(WIZARD.cacheKey(), changed.cacheKey());
  }

  private Object[] changedDeliveryFieldProfiles() {
    return new Object[] {
      new CharacterProfile(
          "Wizard", "Distinguished elderly British accent", "Foolish", "Measured pace"),
      new CharacterProfile("Wizard", "Irish accent", "Warm and knowing", "Measured pace"),
      new CharacterProfile(
          "Wizard", "Distinguished elderly British accent", "Warm and knowing", "Quick pace"),
    };
  }

  @Test
  public void cacheKeyChangesWithThePitchSinceItIsSent() {
    CharacterProfile pitched =
        new CharacterProfile(
            "Wizard",
            "Distinguished elderly British accent",
            null,
            "Warm and knowing",
            "Measured pace",
            "Very deep voice",
            null);
    assertNotEquals(WIZARD.cacheKey(), pitched.cacheKey());
  }

  @Test
  public void anOverriddenAccentLeavesTheCacheKeyToTheVoiceItResolvesTo() {
    CharacterProfile bundled =
        new CharacterProfile("Wizard", "Irish accent", null, "Warm", "Measured", null, "SCOTTISH");
    CharacterProfile overridden =
        new CharacterProfile(
            "Wizard", "Irish accent", null, "Warm", "Measured", null, "SCOTTISH", true);
    assertEquals(bundled.cacheKey(), overridden.cacheKey());
    assertNotEquals(bundled, overridden);
  }

  @Test
  public void punctuationTheStylePromptTrimsNeverChangesTheCacheKey() {
    CharacterProfile tidied =
        new CharacterProfile(
            " Wizard",
            "Distinguished elderly British accent.",
            "Warm and knowing;",
            "Measured pace:");
    assertEquals(WIZARD.cacheKey(), tidied.cacheKey());
  }

  @Test
  public void anAccentDetailThatIsNeverSentNeverChangesTheCacheKey() {
    CharacterProfile accentless =
        new CharacterProfile("Wizard", null, null, "Warm", "Slow", null, null);
    CharacterProfile unsentDetail =
        new CharacterProfile("Wizard", null, "Scholarly RP", "Warm", "Slow", null, null);
    assertEquals(accentless.cacheKey(), unsentDetail.cacheKey());
  }

  @Test
  public void spokenTrimsSurroundingWhitespaceAndTrailingSeparators() {
    assertEquals("Gruff", CharacterProfile.spoken("  Gruff.;, "));
    assertEquals(null, CharacterProfile.spoken(" ... "));
    assertEquals(null, CharacterProfile.spoken(null));
  }

  @Test
  public void theCacheKeyForAFixedProfileNeverChangesAcrossReleases() {
    assertEquals(
        "changing this re-bills every cached line for every user",
        "0c2a6052ac587a6a",
        WIZARD.cacheKey());
  }

  @Test
  public void aProfileIsNotAccentOverriddenByDefault() {
    assertFalse(WIZARD.accentOverridden());
    assertFalse(
        new CharacterProfile("Wizard", "Irish accent", null, "Warm", "Measured", null, "IRISH")
            .accentOverridden());
  }

  @Test
  public void cacheKeyChangesWithTheAccentDetailSinceItIsSent() {
    CharacterProfile detailed =
        new CharacterProfile(
            "Wizard",
            "Distinguished elderly British accent",
            "Received Pronunciation of an old scholar",
            "Warm and knowing",
            "Measured pace",
            null,
            null);
    assertNotEquals(WIZARD.cacheKey(), detailed.cacheKey());
  }

  @Test
  public void aProfileWithoutAccentDetailKeepsItsCacheKey() {
    CharacterProfile plain =
        new CharacterProfile(
            "Wizard",
            "Distinguished elderly British accent",
            null,
            "Warm and knowing",
            "Measured pace",
            null,
            null);
    assertEquals(WIZARD.cacheKey(), plain.cacheKey());
  }

  @Test
  public void cacheKeyChangesWithTheNameSinceItIsSent() {
    CharacterProfile renamed =
        new CharacterProfile(
            "Mage", "Distinguished elderly British accent", "Warm and knowing", "Measured pace");
    assertNotEquals(WIZARD.cacheKey(), renamed.cacheKey());
  }

  @Test
  public void ageIsNotPartOfTheCacheKey() {
    CharacterProfile aged =
        new CharacterProfile(
            "Wizard",
            "Distinguished elderly British accent",
            null,
            "Warm and knowing",
            "Measured pace",
            null,
            null,
            false,
            70);
    assertEquals(WIZARD.cacheKey(), aged.cacheKey());
    assertEquals(Integer.valueOf(70), aged.age());
  }

  @Test
  public void aProfileWithoutAnAgeHasNone() {
    assertEquals(null, WIZARD.age());
  }
}
