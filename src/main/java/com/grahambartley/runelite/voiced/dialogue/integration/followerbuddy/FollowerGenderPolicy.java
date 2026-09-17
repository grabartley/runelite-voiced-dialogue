package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;

public final class FollowerGenderPolicy {

  static final NpcGender FALLBACK = NpcGender.MALE;

  private FollowerGenderPolicy() {}

  public static NpcGender resolve(FollowerVoice configured, NpcGender fromOutfit) {
    if (configured != null && configured.getGender() != NpcGender.UNKNOWN) {
      return configured.getGender();
    }
    return fromOutfit == NpcGender.UNKNOWN || fromOutfit == null ? FALLBACK : fromOutfit;
  }
}
