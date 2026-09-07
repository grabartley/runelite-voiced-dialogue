package com.grahambartley.runelite.voiced.dialogue.profile;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;

/**
 * Formats the debug voice-resolution trace strings. Pure string building, so the whole resolution
 * path (world hit/id, table hit/miss, detected race/gender + source) and the chosen per-NPC variety
 * seed are verifiable without a live client or logger.
 */
public final class VoiceTraceFormatter {

  /** Rendered wherever a field does not apply to the speaker class, or is simply absent. */
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

  /**
   * One consolidated record of the whole resolved decision for a voiced line, so a single grep over
   * {@code [TTS line]} gives the emotion and the full voice metadata (race, gender, seed, profile,
   * accent) actually used for synthesis. The detected npc id and ethnicity stay on the adjacent
   * {@code [TTS profile]}/{@code [TTS voice]} traces, which this complements rather than replaces.
   * A null profile (profiles off) renders {@code -}, as does any field that does not apply to the
   * speaker class: the narrator carries no name, race, gender, life stage, or seed.
   */
  public static String buildResolvedLine(
      String backendId,
      VoiceSpec voice,
      String npcName,
      String emotion,
      String profileName,
      String accent) {
    boolean character = !voice.narrator();
    return String.format(
        "[TTS line] backend=%s kind=%s name=%s emotion=%s race=%s gender=%s lifeStage=%s seed=%s"
            + " profile=%s accent=%s",
        backendId,
        kindOf(voice),
        voice.player() || voice.narrator() ? "-" : "'" + npcName + "'",
        emotion,
        character ? voice.race() : NOT_APPLICABLE,
        character ? voice.gender() : NOT_APPLICABLE,
        character ? (voice.child() ? "child" : "adult") : NOT_APPLICABLE,
        voice.hasVoiceSeed() ? Integer.toString(voice.voiceSeed()) : NOT_APPLICABLE,
        profileName == null ? NOT_APPLICABLE : "'" + profileName + "'",
        accent == null ? NOT_APPLICABLE : "'" + accent + "'");
  }

  private static String kindOf(VoiceSpec voice) {
    if (voice.narrator()) {
      return "narrator";
    }
    return voice.player() ? "player" : "npc";
  }

  static String buildPlayerTrace(NpcGender gender) {
    return String.format("[TTS voice] player -> gender=%s", gender);
  }
}
