package com.grahambartley.runelite.voiced.dialogue.speech.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import org.junit.Test;

public class GeminiSpeechStyleTest {

  private static final CharacterProfile DWARF =
      new CharacterProfile(
          "Dwarf", "Gruff Scottish accent, as heard in Glasgow", "Rough and blunt", "Firm pace");

  @Test
  public void joinsAccentStyleAndPaceIntoSentences() {
    assertEquals(
        "Gruff Scottish accent, as heard in Glasgow. Rough and blunt. Firm pace.",
        GeminiSpeechStyle.compose(DWARF, Emotion.NEUTRAL, null));
  }

  @Test
  public void appendsTheEmotionDirectionAfterTheProfile() {
    assertEquals(
        "Gruff Scottish accent, as heard in Glasgow. Rough and blunt. Firm pace. Sounding angry.",
        GeminiSpeechStyle.compose(DWARF, Emotion.ANGRY, null));
  }

  @Test
  public void appendsASpeedDirectionLast() {
    assertEquals(
        "Gruff Scottish accent, as heard in Glasgow. Rough and blunt. Firm pace. Sounding sad."
            + " Speaking at 150% of normal speed.",
        GeminiSpeechStyle.compose(DWARF, Emotion.SAD, GeminiSpeechStyle.speedDirection(150)));
  }

  @Test
  public void normalisesTrailingPunctuationAndCapitalisesEachDirection() {
    CharacterProfile ragged = new CharacterProfile("Imp", "squeaky accent.", "shrill;  ", "fast,");
    assertEquals("Squeaky accent. Shrill. Fast.", GeminiSpeechStyle.compose(ragged, null, null));
  }

  @Test
  public void skipsMissingAndBlankFields() {
    CharacterProfile sparse = new CharacterProfile("Child", null, "Bright and light", "  ");
    assertEquals("Bright and light.", GeminiSpeechStyle.compose(sparse, Emotion.NEUTRAL, null));
  }

  @Test
  public void anEmptyProfileGivesAnEmptyStyle() {
    CharacterProfile empty = new CharacterProfile("Nobody", null, null, null);
    assertEquals("", GeminiSpeechStyle.compose(empty, null, null));
  }

  @Test
  public void neverCarriesBracketTagsOrTheProfileName() {
    String style = GeminiSpeechStyle.compose(DWARF, Emotion.HAPPY, null);
    assertFalse(style.contains("["));
    assertFalse(style.contains("Dwarf"));
  }
}
