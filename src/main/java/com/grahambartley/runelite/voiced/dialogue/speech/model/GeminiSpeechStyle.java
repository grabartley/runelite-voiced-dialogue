package com.grahambartley.runelite.voiced.dialogue.speech.model;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class GeminiSpeechStyle {

  private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[\\s.;,:]+$");

  private static final String SENTENCE_BREAK = ". ";

  private static final String SENTENCE_END = ".";

  private GeminiSpeechStyle() {}

  static String compose(CharacterProfile profile, Emotion emotion, String paceDirection) {
    List<String> directions = new ArrayList<>();
    addDirection(directions, profile.accent());
    addDirection(directions, profile.style());
    addDirection(directions, profile.pace());
    addDirection(directions, GeminiEmotionStyle.directionFor(emotion));
    addDirection(directions, paceDirection);
    if (directions.isEmpty()) {
      return "";
    }
    return String.join(SENTENCE_BREAK, directions) + SENTENCE_END;
  }

  static String speedDirection(int speedPercent) {
    return "Speaking at " + speedPercent + "% of normal speed";
  }

  private static void addDirection(List<String> directions, String direction) {
    if (direction == null) {
      return;
    }
    String trimmed = TRAILING_PUNCTUATION.matcher(direction.trim()).replaceAll("");
    if (!trimmed.isEmpty()) {
      directions.add(Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1));
    }
  }
}
