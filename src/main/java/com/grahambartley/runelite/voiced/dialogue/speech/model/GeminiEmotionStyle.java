package com.grahambartley.runelite.voiced.dialogue.speech.model;

import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class GeminiEmotionStyle {

  static final EnumSet<Emotion> SUPPORTED =
      EnumSet.of(Emotion.NEUTRAL, Emotion.HAPPY, Emotion.SAD, Emotion.ANGRY, Emotion.SCARED);

  private static final Map<Emotion, String> DIRECTIONS = new EnumMap<>(Emotion.class);

  static {
    DIRECTIONS.put(Emotion.HAPPY, "Sounding happy");
    DIRECTIONS.put(Emotion.SAD, "Sounding sad");
    DIRECTIONS.put(Emotion.ANGRY, "Sounding angry");
    DIRECTIONS.put(Emotion.SCARED, "Sounding fearful");
  }

  private GeminiEmotionStyle() {}

  static String directionFor(Emotion emotion) {
    return emotion == null ? null : DIRECTIONS.get(emotion);
  }
}
