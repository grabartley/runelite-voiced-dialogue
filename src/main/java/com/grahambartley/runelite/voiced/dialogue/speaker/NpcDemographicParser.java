package com.grahambartley.runelite.voiced.dialogue.speaker;

import lombok.extern.slf4j.Slf4j;

/**
 * Maps the raw race/gender strings carried on detected NPC attributes (from the bundled table or a
 * learned wiki lookup) onto the {@link NpcRace}/{@link NpcGender} enums. An exact enum-name match
 * wins, then the bucket name the wiki lookup stores, then a keyword scan over {@link RaceBucket}.
 * An unrecognised race is {@link NpcRace#UNKNOWN}; an unrecognised gender is {@link
 * #DEFAULT_GENDER}.
 */
@Slf4j
public final class NpcDemographicParser {

  /** The gender a speaker voices as whenever detection did not report a female one. */
  static final NpcGender DEFAULT_GENDER = NpcGender.MALE;

  private NpcDemographicParser() {}

  public static NpcRace toRace(String race) {
    if (race == null || race.isEmpty()) {
      return NpcRace.UNKNOWN;
    }

    try {
      return NpcRace.valueOf(race.toUpperCase());
    } catch (IllegalArgumentException notAnEnumName) {
      // Gnome is the one stored bucket with no race of its own: it voices as a goblin.
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

  /** The gender the voice spec carries: female only when detection said so, else the default. */
  public static NpcGender toVoiceGender(NpcGender detected) {
    return detected == NpcGender.FEMALE ? NpcGender.FEMALE : DEFAULT_GENDER;
  }
}
