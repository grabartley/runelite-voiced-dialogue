package com.grahambartley.runelite.voiced.dialogue.speaker;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class NpcDemographicParser {

  static final NpcGender DEFAULT_GENDER = NpcGender.MALE;

  private NpcDemographicParser() {}

  public static NpcRace toRace(String race) {
    if (race == null || race.isEmpty()) {
      return NpcRace.UNKNOWN;
    }

    try {
      return NpcRace.valueOf(race.toUpperCase());
    } catch (IllegalArgumentException notAnEnumName) {
      RaceBucket named = RaceBucket.forBucketName(race);
      if (named != null) {
        return named.race();
      }
      RaceBucket keyword = RaceBucket.forKeyword(race);
      if (keyword != null) {
        return keyword.race();
      }

      log.debug("Unknown race '{}', using default voice", race);
      return NpcRace.UNKNOWN;
    }
  }

  public static NpcGender toGender(String gender) {
    if (gender == null || gender.isEmpty()) {
      return NpcGender.UNKNOWN;
    }

    try {
      return NpcGender.valueOf(gender.toUpperCase());
    } catch (IllegalArgumentException notAnEnumName) {
      String genderLower = gender.toLowerCase();

      if (genderLower.contains("female")
          || genderLower.contains("woman")
          || genderLower.contains("girl")
          || genderLower.contains("lady")) {
        return NpcGender.FEMALE;
      } else if (genderLower.contains("male")
          || genderLower.contains("man")
          || genderLower.contains("boy")
          || genderLower.contains("lord")) {
        return NpcGender.MALE;
      }

      log.debug("Unknown gender '{}', defaulting to MALE", gender);
      return DEFAULT_GENDER;
    }
  }

  public static NpcGender toVoiceGender(NpcGender detected) {
    return detected == NpcGender.FEMALE ? NpcGender.FEMALE : DEFAULT_GENDER;
  }
}
