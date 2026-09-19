package com.grahambartley.runelite.voiced.dialogue.speaker;

public enum NpcGender {
  MALE,
  FEMALE,
  UNKNOWN;

  public static NpcGender orDefault(NpcGender gender) {
    return gender == FEMALE ? FEMALE : MALE;
  }
}
