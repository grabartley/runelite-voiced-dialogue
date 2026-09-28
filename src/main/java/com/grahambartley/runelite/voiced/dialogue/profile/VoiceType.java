package com.grahambartley.runelite.voiced.dialogue.profile;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;

public enum VoiceType {
  TYPE_A(NpcGender.MALE, "Type A"),
  TYPE_B(NpcGender.FEMALE, "Type B");

  private final NpcGender gender;
  private final String label;

  VoiceType(NpcGender gender, String label) {
    this.gender = gender;
    this.label = label;
  }

  public NpcGender getGender() {
    return gender;
  }

  @Override
  public String toString() {
    return label;
  }
}
