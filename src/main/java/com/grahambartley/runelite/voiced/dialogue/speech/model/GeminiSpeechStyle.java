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

  private static final String CHARACTER_FRAME = ", a character in a medieval fantasy world";

  private GeminiSpeechStyle() {}

  static String compose(CharacterProfile profile, Emotion emotion, String paceDirection) {
    List<String> directions = new ArrayList<>();
    String name = clean(profile.name());
    if (name != null) {
      directions.add(sentence("Audio profile: " + name + CHARACTER_FRAME));
    }
    addLabelled(directions, "Accent", profile.accent());
    addLabelled(directions, "Style", profile.style());
    addLabelled(directions, "Pace", profile.pace());
    addDirection(directions, GeminiEmotionStyle.directionFor(emotion));
    addDirection(directions, paceDirection);
    return String.join(" ", directions);
  }

  static String speedDirection(int speedPercent) {
    return "Speaking at " + speedPercent + "% of normal speed";
  }

  private static void addLabelled(List<String> directions, String label, String value) {
    String cleaned = clean(value);
    if (cleaned != null) {
      directions.add(sentence(label + ": " + cleaned));
    }
  }

  private static void addDirection(List<String> directions, String direction) {
    String cleaned = clean(direction);
    if (cleaned != null) {
      directions.add(sentence(Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1)));
    }
  }

  private static String clean(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = TRAILING_SEPARATORS.matcher(value.trim()).replaceAll("");
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String sentence(String text) {
    return TERMINAL_PUNCTUATION.matcher(text).find() ? text : text + FULL_STOP;
  }
}
