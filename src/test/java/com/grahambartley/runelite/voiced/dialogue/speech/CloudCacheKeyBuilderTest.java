package com.grahambartley.runelite.voiced.dialogue.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import org.junit.Test;

public class CloudCacheKeyBuilderTest {

  private static final VoiceSpec MAN = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE);

  private static String build(
      String voice, int speedPercent, CharacterProfile profile, String languageToken) {
    return CloudCacheKeyBuilder.build(voice, MAN, speedPercent, profile, languageToken);
  }

  @Test
  public void baseKeyIsVoiceSpeakerAndProfileWithNoOptionalFragments() {
    assertEquals(
        "every speaker resolves to a profile, so the profile fragment is part of the base key",
        "v|aMALE|p" + TestFixtures.TROLL_PROFILE.cacheKey(),
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
  public void aChildAndAnAdultOnTheSameVoiceNeverShareAKey() {
    VoiceSpec boy = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 1, true);
    assertNotEquals(
        CloudCacheKeyBuilder.build("Puck", MAN, 100, TestFixtures.TROLL_PROFILE, null),
        CloudCacheKeyBuilder.build("Puck", boy, 100, TestFixtures.TROLL_PROFILE, null));
  }

  @Test
  public void theSpeakerTokenIsBuiltFromEnumNamesOnly() {
    assertEquals("MALE", CloudCacheKeyBuilder.speakerToken(MAN));
    assertEquals("MALE", CloudCacheKeyBuilder.speakerToken(VoiceSpec.player(NpcGender.MALE)));
    assertEquals(
        "CHILD_FEMALE",
        CloudCacheKeyBuilder.speakerToken(
            VoiceSpec.npc(NpcRace.GOBLIN, NpcGender.FEMALE, 3, true)));
    assertEquals("NARRATOR", CloudCacheKeyBuilder.speakerToken(VoiceSpec.NARRATOR));
  }

  @Test
  public void theRaceIsNotPartOfTheKey() {
    assertEquals(
        build("v", 100, TestFixtures.TROLL_PROFILE, null),
        CloudCacheKeyBuilder.build(
            "v",
            VoiceSpec.npc(NpcRace.TROLL, NpcGender.MALE),
            100,
            TestFixtures.TROLL_PROFILE,
            null));
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
  public void fragmentsAreAppendedInVoiceSpeakerSpeedProfileLanguageOrder() {
    assertEquals(
        "v|aMALE|s150|p" + TestFixtures.TROLL_PROFILE.cacheKey() + "|lFRENCH+PIRATE",
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
        "en-gb-tutor-9|aMALE|p18cbc67f87f11541",
        build("en-gb-tutor-9", 100, bartender, null));
  }
}
