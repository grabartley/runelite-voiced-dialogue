package com.grahambartley.runelite.voiced.dialogue.profile;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;

public final class VoiceTraceFormatter {

  private static final String NOT_APPLICABLE = "-";

  private VoiceTraceFormatter() {}

  static String buildNpcTrace(
      String npcName,
      Integer npcId,
      NpcRace race,
      NpcGender gender,
      boolean child,
      String source,
      int seed) {
    return String.format(
        "[TTS voice] npc='%s' world=%s race=%s gender=%s lifeStage=%s source=%s -> seed=%d",
        npcName,
        npcId == null ? "MISS" : "HIT(id=" + npcId + ")",
        race,
        gender,
        child ? "child" : "adult",
        source,
        seed);
  }

  public static String buildResolvedLine(
      String backendId, VoiceSpec voice, String npcName, String emotion, CharacterProfile profile) {
    boolean character = !voice.narrator();
    return String.format(
        "[TTS line] backend=%s kind=%s name=%s emotion=%s race=%s gender=%s lifeStage=%s seed=%s"
            + " profile=%s accent=%s",
        backendId,
        kindOf(voice),
        voice.kind() == VoiceSpec.Kind.NPC ? "'" + npcName + "'" : NOT_APPLICABLE,
        emotion,
        character ? voice.race() : NOT_APPLICABLE,
        character ? voice.gender() : NOT_APPLICABLE,
        character ? (voice.child() ? "child" : "adult") : NOT_APPLICABLE,
        voice.hasVoiceSeed() ? Integer.toString(voice.voiceSeed()) : NOT_APPLICABLE,
        "'" + profile.name() + "'",
        "'" + profile.accent() + "'");
  }

  private static String kindOf(VoiceSpec voice) {
    switch (voice.kind()) {
      case NARRATOR:
        return "narrator";
      case PLAYER:
        return "player";
      case FOLLOWER:
        return "follower";
      default:
        return "npc";
    }
  }

  static String buildFollowerTrace(NpcGender gender) {
    return String.format("[TTS voice] follower -> gender=%s", gender);
  }

  static String buildPlayerTrace(NpcGender gender) {
    return String.format("[TTS voice] player -> gender=%s", gender);
  }
}
