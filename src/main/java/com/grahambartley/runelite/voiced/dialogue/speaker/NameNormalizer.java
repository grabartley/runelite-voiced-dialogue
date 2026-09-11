package com.grahambartley.runelite.voiced.dialogue.speaker;

import java.util.regex.Pattern;

public final class NameNormalizer {

  private static final Pattern TAG = Pattern.compile("<[^>]*>");

  private static final char NON_BREAKING_SPACE = ' ';

  private NameNormalizer() {}

  public static String normalize(String name) {
    if (name == null) {
      return "";
    }
    return TAG.matcher(name).replaceAll("").replace(NON_BREAKING_SPACE, ' ').trim();
  }
}
