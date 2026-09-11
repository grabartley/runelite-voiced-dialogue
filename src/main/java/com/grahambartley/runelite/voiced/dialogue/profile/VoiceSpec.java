package com.grahambartley.runelite.voiced.dialogue.profile;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class VoiceSpec {

  public static final int UNSPECIFIED_SEED = -1;

  public static final VoiceSpec NARRATOR =
      new VoiceSpec(false, NpcRace.HUMAN, NpcGender.MALE, UNSPECIFIED_SEED, false, true);

  boolean player;
  NpcRace race;
  NpcGender gender;
  int voiceSeed;
  boolean child;
  boolean narrator;

  public static VoiceSpec player(NpcGender gender) {
    return new VoiceSpec(true, NpcRace.HUMAN, gender, UNSPECIFIED_SEED, false, false);
  }

  public static VoiceSpec npc(NpcRace race, NpcGender gender) {
    return new VoiceSpec(false, race, gender, UNSPECIFIED_SEED, false, false);
  }

  public static VoiceSpec npc(NpcRace race, NpcGender gender, int seed) {
    return new VoiceSpec(false, race, gender, seed < 0 ? UNSPECIFIED_SEED : seed, false, false);
  }

  public static VoiceSpec npc(NpcRace race, NpcGender gender, int seed, boolean child) {
    return new VoiceSpec(false, race, gender, seed < 0 ? UNSPECIFIED_SEED : seed, child, false);
  }

  public boolean hasVoiceSeed() {
    return voiceSeed >= 0;
  }

  public String key() {
    if (narrator) {
      return "narrator";
    }
    return player ? "player:" + gender : "npc:" + race + ":" + gender;
  }

  @Override
  public String toString() {
    return key();
  }
}
