package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.EnumSet;
import org.junit.Test;

/** Emotion -> Gemini inline style tag mapping and the neutral no-tag passthrough. */
public class GeminiEmotionStyleTest {

  @Test
  public void supportsEveryDetectedEmotion() {
    assertEquals(
        EnumSet.of(Emotion.NEUTRAL, Emotion.HAPPY, Emotion.SAD, Emotion.ANGRY, Emotion.SCARED),
        GeminiEmotionStyle.SUPPORTED);
  }

  @Test
  public void mapsEachNonNeutralEmotionToItsConservativeTag() {
    assertEquals("happy", GeminiEmotionStyle.tagFor(Emotion.HAPPY));
    assertEquals("sad", GeminiEmotionStyle.tagFor(Emotion.SAD));
    assertEquals("angry", GeminiEmotionStyle.tagFor(Emotion.ANGRY));
    assertEquals("fearful", GeminiEmotionStyle.tagFor(Emotion.SCARED));
  }

  @Test
  public void neutralAndNullHaveNoTag() {
    assertNull(GeminiEmotionStyle.tagFor(Emotion.NEUTRAL));
    assertNull(GeminiEmotionStyle.tagFor(null));
  }

  @Test
  public void applyPrependsTheBracketedTag() {
    assertEquals("[angry] Get out!", GeminiEmotionStyle.apply("Get out!", Emotion.ANGRY));
    assertEquals("[fearful] Help me!", GeminiEmotionStyle.apply("Help me!", Emotion.SCARED));
  }

  @Test
  public void applyLeavesNeutralInputUntouched() {
    String text = "Well met, traveller.";
    assertSame(
        "neutral returns the same string instance",
        text,
        GeminiEmotionStyle.apply(text, Emotion.NEUTRAL));
    assertSame(text, GeminiEmotionStyle.apply(text, null));
  }
}
