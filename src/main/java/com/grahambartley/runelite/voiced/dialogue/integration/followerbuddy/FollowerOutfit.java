package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import java.util.Locale;
import java.util.regex.Pattern;

public final class FollowerOutfit {

  private static final Pattern TOKENS = Pattern.compile("[,\\r\\n]+");

  private static final String GENDER_KEY = "gender";

  private FollowerOutfit() {}

  public static NpcGender genderOf(String outfit) {
    if (outfit == null || outfit.isEmpty()) {
      return NpcGender.UNKNOWN;
    }
    for (String token : TOKENS.split(outfit)) {
      int split = token.indexOf('=');
      if (split < 0 || !GENDER_KEY.equalsIgnoreCase(token.substring(0, split).trim())) {
        continue;
      }
      String value = token.substring(split + 1).trim().toLowerCase(Locale.ROOT);
      if (value.isEmpty()) {
        return NpcGender.UNKNOWN;
      }
      return value.startsWith("f") || "1".equals(value) ? NpcGender.FEMALE : NpcGender.MALE;
    }
    return NpcGender.UNKNOWN;
  }
}
