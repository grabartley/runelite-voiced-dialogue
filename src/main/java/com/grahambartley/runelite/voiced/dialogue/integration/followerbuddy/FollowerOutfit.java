package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FollowerOutfit {

  private static final Pattern GENDER =
      Pattern.compile("(?:^|,)\\s*gender\\s*=\\s*([A-Za-z]+)\\s*(?=,|$)");

  private FollowerOutfit() {}

  public static NpcGender genderOf(String outfit) {
    if (outfit == null || outfit.isEmpty()) {
      return NpcGender.UNKNOWN;
    }
    Matcher matcher = GENDER.matcher(outfit);
    if (!matcher.find()) {
      return NpcGender.UNKNOWN;
    }
    String value = matcher.group(1).toLowerCase(Locale.ROOT);
    if ("female".equals(value)) {
      return NpcGender.FEMALE;
    }
    return "male".equals(value) ? NpcGender.MALE : NpcGender.UNKNOWN;
  }
}
