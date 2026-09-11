package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class EmotionResolverTest {

  private final EmotionResolver resolver = new EmotionResolver();

  private Object[] resolveCases() {
    return new Object[] {
      new Object[] {614, true, Emotion.ANGRY},
      new Object[] {596, true, Emotion.SCARED},
      new Object[] {567, true, Emotion.HAPPY},
      new Object[] {610, true, Emotion.SAD},
      new Object[] {588, true, Emotion.NEUTRAL},
      new Object[] {614, false, Emotion.NEUTRAL},
      new Object[] {567, false, Emotion.NEUTRAL},
      new Object[] {-1, true, Emotion.NEUTRAL},
      new Object[] {123456, true, Emotion.NEUTRAL},
    };
  }

  @Test
  @Parameters(method = "resolveCases")
  public void resolvesExpressionIdToEmotion(
      int expressionId, boolean emotionEnabled, Emotion expected) {
    assertEquals(expected, resolver.resolve(expressionId, emotionEnabled));
  }
}
