package com.grahambartley.runelite.voiced.dialogue.speech.model;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class GeminiSpeechStyle {

  private static final Pattern TRAILING_SEPARATORS = Pattern.compile("[\\s.;,:]+$");

  private static final Pattern TERMINAL_PUNCTUATION = Pattern.compile("[!?]$");

  private static final String FULL_STOP = ".";

  private GeminiSpeechStyle() {}

  static String compose(CharacterProfile profile, Emotion emotion, String paceDirection) {
    List<String> directions = new ArrayList<>();
    addDirection(directions, profile.accent());
    addDirection(directions, profile.style());
    addDirection(directions, profile.pace());
    addDirection(directions, GeminiEmotionStyle.directionFor(emotion));
    addDirection(directions, paceDirection);
    return String.join(" ", directions);
  }

  static String speedDirection(int speedPercent) {
    return "Speaking at " + speedPercent + "% of normal speed";
  }

  private static void addDirection(List<String> directions, String direction) {
    if (direction == null) {
      return;
    }
    String trimmed = TRAILING_SEPARATORS.matcher(direction.trim()).replaceAll("");
    if (trimmed.isEmpty()) {
      return;
    }
    String capitalised = Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    boolean terminated = TERMINAL_PUNCTUATION.matcher(capitalised).find();
    directions.add(terminated ? capitalised : capitalised + FULL_STOP);
  }
}
