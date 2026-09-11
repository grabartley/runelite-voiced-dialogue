package com.grahambartley.runelite.voiced.dialogue.speech.model;

import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class GeminiEmotionStyle {

  static final EnumSet<Emotion> SUPPORTED =
      EnumSet.of(Emotion.NEUTRAL, Emotion.HAPPY, Emotion.SAD, Emotion.ANGRY, Emotion.SCARED);

  private static final Map<Emotion, String> TAGS = new EnumMap<>(Emotion.class);

  static {
    TAGS.put(Emotion.HAPPY, "happy");
    TAGS.put(Emotion.SAD, "sad");
    TAGS.put(Emotion.ANGRY, "angry");
    TAGS.put(Emotion.SCARED, "fearful");
  }

  private GeminiEmotionStyle() {}

  public static String tagFor(Emotion emotion) {
    return emotion == null ? null : TAGS.get(emotion);
  }

  static String apply(String input, Emotion emotion) {
    String tag = tagFor(emotion);
    if (tag == null) {
      return input;
    }
    return "[" + tag + "] " + input;
  }
}
