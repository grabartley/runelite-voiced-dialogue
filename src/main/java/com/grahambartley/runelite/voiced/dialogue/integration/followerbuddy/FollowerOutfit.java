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
    NpcGender gender = NpcGender.UNKNOWN;
    for (String token : TOKENS.split(outfit)) {
      String trimmed = token.trim();
      if (trimmed.startsWith("#") || trimmed.startsWith("//")) {
        continue;
      }
      int split = trimmed.indexOf('=');
      if (split < 0 || !GENDER_KEY.equalsIgnoreCase(trimmed.substring(0, split).trim())) {
        continue;
      }
      String value = trimmed.substring(split + 1).trim().toLowerCase(Locale.ROOT);
      gender =
          value.isEmpty()
              ? NpcGender.UNKNOWN
              : (value.startsWith("f") || "1".equals(value) ? NpcGender.FEMALE : NpcGender.MALE);
    }
    return gender;
  }
}
