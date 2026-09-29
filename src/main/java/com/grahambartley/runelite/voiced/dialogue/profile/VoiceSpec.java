package com.grahambartley.runelite.voiced.dialogue.profile;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class VoiceSpec {

  public static final int UNSPECIFIED_SEED = -1;

  public enum Kind {
    NPC,
    PLAYER,
    FOLLOWER,
    NARRATOR
  }

  public static final VoiceSpec NARRATOR =
      new VoiceSpec(Kind.NARRATOR, NpcRace.HUMAN, NpcGender.MALE, UNSPECIFIED_SEED, false);

  Kind kind;
  NpcRace race;
  NpcGender gender;
  int voiceSeed;
  boolean child;

  public static VoiceSpec player(NpcGender gender) {
    return new VoiceSpec(Kind.PLAYER, NpcRace.HUMAN, gender, UNSPECIFIED_SEED, false);
  }

  public static VoiceSpec follower(NpcGender gender) {
    return new VoiceSpec(Kind.FOLLOWER, NpcRace.HUMAN, gender, UNSPECIFIED_SEED, false);
  }

  public static VoiceSpec npc(NpcRace race, NpcGender gender) {
    return new VoiceSpec(Kind.NPC, race, gender, UNSPECIFIED_SEED, false);
  }

  public static VoiceSpec npc(NpcRace race, NpcGender gender, int seed) {
    return new VoiceSpec(Kind.NPC, race, gender, seed < 0 ? UNSPECIFIED_SEED : seed, false);
  }

  public static VoiceSpec npc(NpcRace race, NpcGender gender, int seed, boolean child) {
    return new VoiceSpec(Kind.NPC, race, gender, seed < 0 ? UNSPECIFIED_SEED : seed, child);
  }

  public boolean player() {
    return kind == Kind.PLAYER;
  }

  public boolean follower() {
    return kind == Kind.FOLLOWER;
  }

  public boolean narrator() {
    return kind == Kind.NARRATOR;
  }

  public boolean hasVoiceSeed() {
    return voiceSeed >= 0;
  }

  public String key() {
    switch (kind) {
      case NARRATOR:
        return "narrator";
      case PLAYER:
        return "player:" + gender;
      case FOLLOWER:
        return "follower:" + gender;
      default:
        return "npc:" + race + ":" + gender;
    }
  }

  @Override
  public String toString() {
    return key();
  }
}
