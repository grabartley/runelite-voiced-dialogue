package com.grahambartley.runelite.voiced.dialogue.speech.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import org.junit.Test;

public class GeminiSpeechStyleTest {

  private static final CharacterProfile DWARF =
      new CharacterProfile(
          "Keldagrim Dwarf",
          "Strong Glasgow Scottish accent, Scottish English pronunciation",
          "A stout, hard-bitten mountain dwarf. Rough, gravelly, and blunt.",
          "Firm and forthright.");

  private static final String DWARF_PROFILE =
      "Audio profile: Keldagrim Dwarf, a character in a medieval fantasy world."
          + " Accent: Strong Glasgow Scottish accent, Scottish English pronunciation."
          + " Style: A stout, hard-bitten mountain dwarf. Rough, gravelly, and blunt."
          + " Pace: Firm and forthright.";

  @Test
  public void rendersTheFullProfileWithLabelledFields() {
    assertEquals(DWARF_PROFILE, GeminiSpeechStyle.compose(DWARF, Emotion.NEUTRAL, null));
  }

  @Test
  public void pitchOpensTheStyleBeforeTheProfile() {
    CharacterProfile goblin =
        new CharacterProfile(
            "Goblin",
            "Strong East London accent, British English pronunciation",
            "Crude and mischievous.",
            "Quick.",
            "Very high-pitched, squeaky voice",
            null);
    assertEquals(
        "Very high-pitched, squeaky voice. Audio profile: Goblin, a character in a medieval fantasy"
            + " world. Accent: Strong East London accent, British English pronunciation."
            + " Style: Crude and mischievous. Pace: Quick.",
        GeminiSpeechStyle.compose(goblin, Emotion.NEUTRAL, null));
  }

  @Test
  public void appendsTheEmotionDirectionAfterTheProfile() {
    assertEquals(
        DWARF_PROFILE + " Sounding angry.", GeminiSpeechStyle.compose(DWARF, Emotion.ANGRY, null));
  }

  @Test
  public void appendsASpeedDirectionLast() {
    assertEquals(
        DWARF_PROFILE + " Sounding sad. Speaking at 150% of normal speed.",
        GeminiSpeechStyle.compose(DWARF, Emotion.SAD, GeminiSpeechStyle.speedDirection(150)));
  }

  @Test
  public void normalisesTrailingPunctuationToOneFullStop() {
    CharacterProfile ragged = new CharacterProfile("Imp;", "squeaky accent.", "shrill;  ", "fast,");
    assertEquals(
        "Audio profile: Imp, a character in a medieval fantasy world. Accent: squeaky accent."
            + " Style: shrill. Pace: fast.",
        GeminiSpeechStyle.compose(ragged, null, null));
  }

  @Test
  public void keepsAnExclamationOrQuestionMarkAsTheSentenceEnd() {
    CharacterProfile loud = new CharacterProfile(null, "loud accent!", "curious?", "brisk");
    assertEquals(
        "Accent: loud accent! Style: curious? Pace: brisk.",
        GeminiSpeechStyle.compose(loud, null, null));
  }

  @Test
  public void skipsMissingAndBlankFields() {
    CharacterProfile sparse = new CharacterProfile(null, null, "Bright and light", "  ");
    assertEquals(
        "Style: Bright and light.", GeminiSpeechStyle.compose(sparse, Emotion.NEUTRAL, null));
  }

  @Test
  public void anEmptyProfileGivesAnEmptyStyle() {
    CharacterProfile empty = new CharacterProfile(null, null, null, null);
    assertEquals("", GeminiSpeechStyle.compose(empty, null, null));
  }

  @Test
  public void neverCarriesBracketTags() {
    assertFalse(GeminiSpeechStyle.compose(DWARF, Emotion.HAPPY, null).contains("["));
  }
}
