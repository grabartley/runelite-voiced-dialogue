package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;

public final class FollowerGenderPolicy {

  private FollowerGenderPolicy() {}

  public static NpcGender resolve(FollowerVoice configured, NpcGender fromOutfit) {
    if (configured != null && configured.getGender() != NpcGender.UNKNOWN) {
      return configured.getGender();
    }
    return NpcGender.orDefault(fromOutfit);
  }
}
