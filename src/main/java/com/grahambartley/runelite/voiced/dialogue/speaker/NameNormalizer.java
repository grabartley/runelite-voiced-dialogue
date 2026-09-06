package com.grahambartley.runelite.voiced.dialogue.speaker;

import java.util.regex.Pattern;

/**
 * Normalises an NPC name for tolerant matching: strips any {@code <...>} tags, converts
 * non-breaking spaces to regular spaces, and trims. Case is left to the caller's comparison. This
 * stops cosmetic markup on the dialogue name widget from forcing a false miss against the raw
 * composition name.
 */
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
