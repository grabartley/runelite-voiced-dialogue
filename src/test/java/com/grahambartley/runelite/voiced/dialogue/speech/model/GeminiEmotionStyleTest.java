package com.grahambartley.runelite.voiced.dialogue.speech.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import java.util.EnumSet;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class GeminiEmotionStyleTest {

  @Test
  public void supportsEveryDetectedEmotion() {
    assertEquals(
        EnumSet.of(Emotion.NEUTRAL, Emotion.HAPPY, Emotion.SAD, Emotion.ANGRY, Emotion.SCARED),
        GeminiEmotionStyle.SUPPORTED);
  }

  private Object[] directionCases() {
    return new Object[] {
      new Object[] {Emotion.HAPPY, "Sounding happy"},
      new Object[] {Emotion.SAD, "Sounding sad"},
      new Object[] {Emotion.ANGRY, "Sounding angry"},
      new Object[] {Emotion.SCARED, "Sounding fearful"},
    };
  }

  @Test
  @Parameters(method = "directionCases")
  public void mapsEachNonNeutralEmotionToAStyleDirection(Emotion emotion, String expected) {
    assertEquals(expected, GeminiEmotionStyle.directionFor(emotion));
  }

  private Object[] undirectedEmotions() {
    return new Object[] {Emotion.NEUTRAL, null};
  }

  @Test
  @Parameters(method = "undirectedEmotions")
  public void neutralAndNullAddNoDirection(Emotion emotion) {
    assertNull(GeminiEmotionStyle.directionFor(emotion));
  }
}
