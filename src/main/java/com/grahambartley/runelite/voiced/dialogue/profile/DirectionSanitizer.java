package com.grahambartley.runelite.voiced.dialogue.profile;

import java.util.regex.Pattern;

public final class DirectionSanitizer {

  static final int MAX_FIELD_LENGTH = 1000;

  private static final Pattern CONTROL = Pattern.compile("[\\u0000-\\u001F\\u007F]+");

  private static final Pattern TAG_BRACKETS = Pattern.compile("[\\[\\]<>]");

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private final ProfanityFilter profanityFilter;

  public DirectionSanitizer(ProfanityFilter profanityFilter) {
    this.profanityFilter = profanityFilter;
  }

  public String sanitize(String field) {
    if (field == null) {
      return null;
    }
    String flattened = CONTROL.matcher(field).replaceAll(" ");
    String demarked = TAG_BRACKETS.matcher(flattened).replaceAll(" ");
    String collapsed = WHITESPACE.matcher(demarked).replaceAll(" ").trim();
    if (collapsed.length() > MAX_FIELD_LENGTH) {
      collapsed = collapsed.substring(0, MAX_FIELD_LENGTH).trim();
    }
    return profanityFilter.mask(collapsed);
  }
}
