package com.grahambartley.runelite.voiced.dialogue.speech.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import org.junit.Test;

public class GeminiSpeechStyleTest {

  private static final String ENGLISH = "English";

  private static final VoiceSpec MAN = VoiceSpec.npc(NpcRace.DWARF, NpcGender.MALE, 1);

  private static final VoiceSpec WOMAN = VoiceSpec.npc(NpcRace.TROLL, NpcGender.FEMALE, 1);

  private static final VoiceSpec UNKNOWN = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.UNKNOWN, 1);

  private static final CharacterProfile DWARF =
      new CharacterProfile(
          "Keldagrim Dwarf",
          "Strong Glasgow Scottish accent, Scottish English pronunciation",
          "A stout, hard-bitten mountain dwarf. Rough, gravelly, and blunt.",
          "Firm and forthright.");

  private static final String DWARF_PROFILE =
      "Speaking English. A man's voice. Audio profile: Keldagrim Dwarf, a character in a medieval"
          + " fantasy world."
          + " Accent: Strong Glasgow Scottish accent, Scottish English pronunciation."
          + " Style: A stout, hard-bitten mountain dwarf. Rough, gravelly, and blunt."
          + " Pace: Firm and forthright.";

  private static final String DEEP = "Very deep, booming voice";

  private static CharacterProfile pitched(String pitch) {
    return new CharacterProfile("Aga", null, null, null, null, pitch, null);
  }

  private static String compose(CharacterProfile profile, VoiceSpec voice, Emotion emotion) {
    return GeminiSpeechStyle.compose(profile, voice, emotion, ENGLISH, null);
  }

  @Test
  public void rendersTheFullProfileWithLabelledFields() {
    assertEquals(DWARF_PROFILE, compose(DWARF, MAN, Emotion.NEUTRAL));
  }

  @Test
  public void opensWithTheSpokenLanguage() {
    assertEquals(
        "Speaking French. Style: Bright.",
        GeminiSpeechStyle.compose(
            new CharacterProfile(null, null, "Bright", null),
            UNKNOWN,
            Emotion.NEUTRAL,
            "French",
            null));
  }

  @Test
  public void pitchFollowsTheVoicesGenderSoADeepWomanStaysAWoman() {
    assertEquals(
        "Speaking English. A woman's voice. Very deep, booming voice. Audio profile: Aga, a"
            + " character in a medieval fantasy world.",
        compose(pitched(DEEP), WOMAN, Emotion.NEUTRAL));
    assertEquals(
        "Speaking English. A man's voice. Very deep, booming voice. Audio profile: Aga, a"
            + " character in a medieval fantasy world.",
        compose(pitched(DEEP), MAN, Emotion.NEUTRAL));
  }

  @Test
  public void aChildsPitchNamesABoyOrAGirl() {
    VoiceSpec boy = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 1, true);
    VoiceSpec girl = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.FEMALE, 1, true);
    assertEquals(
        "Speaking English. A young boy's voice. High. Audio profile: Aga, a character in a"
            + " medieval fantasy world.",
        compose(pitched("High"), boy, Emotion.NEUTRAL));
    assertEquals(
        "Speaking English. A young girl's voice. High. Audio profile: Aga, a character in a"
            + " medieval fantasy world.",
        compose(pitched("High"), girl, Emotion.NEUTRAL));
  }

  @Test
  public void everyLineNamesTheGenderEvenWithoutAPitch() {
    assertTrue(
        compose(DWARF, WOMAN, Emotion.NEUTRAL).startsWith("Speaking English. A woman's voice."));
  }

  @Test
  public void theNarratorsLineNamesNoGender() {
    assertEquals(
        "Speaking English. Style: Bright.",
        compose(new CharacterProfile(null, null, "Bright", null), VoiceSpec.NARRATOR, null));
  }

  @Test
  public void anUnknownGenderKeepsThePitchWithoutAGenderDirection() {
    VoiceSpec unknown = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.UNKNOWN, 1);
    assertEquals(
        "Speaking English. High. Audio profile: Aga, a character in a medieval fantasy world.",
        compose(pitched("High"), unknown, Emotion.NEUTRAL));
  }

  @Test
  public void theAccentDetailFollowsTheAccent() {
    CharacterProfile detailed =
        new CharacterProfile(
            null,
            "Strong rough, harsh English accent, British English pronunciation",
            "a rough, harsh British English, the hard edge of the outlaws",
            null,
            null,
            null,
            null);
    assertEquals(
        "Speaking English. Accent: Strong rough, harsh English accent, British English"
            + " pronunciation. A rough, harsh British English, the hard edge of the outlaws.",
        compose(detailed, UNKNOWN, Emotion.NEUTRAL));
  }

  @Test
  public void anAccentDetailWithoutAnAccentIsNotSent() {
    CharacterProfile orphan =
        new CharacterProfile(null, null, "a lilt", "Bright", null, null, null);
    assertEquals("Speaking English. Style: Bright.", compose(orphan, UNKNOWN, Emotion.NEUTRAL));
  }

  @Test
  public void appendsTheEmotionDirectionAfterTheProfile() {
    assertEquals(DWARF_PROFILE + " Sounding angry.", compose(DWARF, MAN, Emotion.ANGRY));
  }

  @Test
  public void appendsASpeedDirectionLast() {
    assertEquals(
        DWARF_PROFILE + " Sounding sad. Speaking at 150% of normal speed.",
        GeminiSpeechStyle.compose(
            DWARF, MAN, Emotion.SAD, ENGLISH, GeminiSpeechStyle.speedDirection(150)));
  }

  @Test
  public void normalisesTrailingPunctuationToOneFullStop() {
    CharacterProfile ragged = new CharacterProfile("Imp;", "squeaky accent.", "shrill;  ", "fast,");
    assertEquals(
        "Speaking English. Audio profile: Imp, a character in a medieval fantasy world. Accent:"
            + " squeaky accent. Style: shrill. Pace: fast.",
        compose(ragged, UNKNOWN, null));
  }

  @Test
  public void keepsAnExclamationOrQuestionMarkAsTheSentenceEnd() {
    CharacterProfile loud = new CharacterProfile(null, "loud accent!", "curious?", "brisk");
    assertEquals(
        "Speaking English. Accent: loud accent! Style: curious? Pace: brisk.",
        compose(loud, UNKNOWN, null));
  }

  @Test
  public void skipsMissingAndBlankFields() {
    CharacterProfile sparse = new CharacterProfile(null, null, "Bright and light", "  ");
    assertEquals(
        "Speaking English. Style: Bright and light.", compose(sparse, UNKNOWN, Emotion.NEUTRAL));
  }

  @Test
  public void anEmptyProfileStillNamesTheLanguage() {
    CharacterProfile empty = new CharacterProfile(null, null, null, null);
    assertEquals("Speaking English.", compose(empty, UNKNOWN, null));
  }

  @Test
  public void neverCarriesBracketTags() {
    assertFalse(compose(DWARF, MAN, Emotion.HAPPY).contains("["));
  }
}
