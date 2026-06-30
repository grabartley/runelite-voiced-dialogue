package com.grahambartley;

import static org.junit.Assert.assertEquals;

import com.grahambartley.synthesis.Emotion;
import org.junit.Test;

/**
 * Covers the emotion-decision seam {@link EmotionResolver#resolve(int, boolean)} backing #26's
 * chat-head detection. The table is the real bundled {@code expression-emotions.json} (loaded at
 * construction, no client needed), so these exercise the same mapped ids the live widget read feeds
 * in. The raw widget read itself ({@link DialogueWidgetReader}) is covered separately.
 */
public class EmotionResolverTest {

  private final EmotionResolver resolver = new EmotionResolver();

  /** A mapped expression id with emotion enabled resolves to the table's emotion. */
  @Test
  public void mappedIdWithEmotionEnabledResolvesToThatEmotion() {
    assertEquals(Emotion.ANGRY, resolver.resolve(614, true));
    assertEquals(Emotion.SCARED, resolver.resolve(596, true));
    assertEquals(Emotion.HAPPY, resolver.resolve(567, true));
    assertEquals(Emotion.SAD, resolver.resolve(610, true));
    assertEquals(Emotion.NEUTRAL, resolver.resolve(588, true));
  }

  /** The emotion gate forces NEUTRAL even for an id that maps to a real emotion. */
  @Test
  public void emotionDisabledForcesNeutral() {
    assertEquals(Emotion.NEUTRAL, resolver.resolve(614, false));
    assertEquals(Emotion.NEUTRAL, resolver.resolve(567, false));
  }

  /** {@code -1} (missing head, sprite dialogue, or one-tick race) resolves to NEUTRAL. */
  @Test
  public void noExpressionResolvesToNeutral() {
    assertEquals(Emotion.NEUTRAL, resolver.resolve(-1, true));
  }

  /** An id outside the documented table (e.g. a non-human head) resolves to NEUTRAL. */
  @Test
  public void unmappedIdResolvesToNeutral() {
    assertEquals(Emotion.NEUTRAL, resolver.resolve(123456, true));
  }
}
