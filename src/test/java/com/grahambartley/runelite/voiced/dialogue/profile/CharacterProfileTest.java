package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
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
  @Parameters(method = "changedSpokenFieldProfiles")
  public void cacheKeyChangesWhenAnySpokenFieldChanges(CharacterProfile changed) {
    assertNotEquals(WIZARD.cacheKey(), changed.cacheKey());
  }

  private Object[] changedSpokenFieldProfiles() {
    return new Object[] {
      new CharacterProfile(
          "Wizard", "Distinguished elderly British accent", "Foolish", "Measured pace"),
      new CharacterProfile("Wizard", "Irish accent", "Warm and knowing", "Measured pace"),
      new CharacterProfile(
          "Wizard", "Distinguished elderly British accent", "Warm and knowing", "Quick pace"),
    };
  }

  @Test
  public void cacheKeyIgnoresTheNameSinceItIsNeverSent() {
    CharacterProfile renamed =
        new CharacterProfile(
            "Mage", "Distinguished elderly British accent", "Warm and knowing", "Measured pace");
    assertEquals(WIZARD.cacheKey(), renamed.cacheKey());
  }
}
