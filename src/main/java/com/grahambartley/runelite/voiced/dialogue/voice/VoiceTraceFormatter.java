package com.grahambartley.runelite.voiced.dialogue.voice;

/**
 * Formats the debug voice-resolution trace strings. Pure string building, so the whole resolution
 * path (world hit/id, table hit/miss, detected race/gender + source) and the chosen per-NPC variety
 * seed are verifiable without a live client or logger.
 */
public final class VoiceTraceFormatter {

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
   * A null profile (profiles off) renders {@code -}.
   */
  public static String buildResolvedLine(
      String backendId,
      boolean player,
      String npcName,
      String emotion,
      NpcRace race,
      NpcGender gender,
      boolean child,
      int seed,
      String profileName,
      String accent) {
    return String.format(
        "[TTS line] backend=%s kind=%s name=%s emotion=%s race=%s gender=%s lifeStage=%s seed=%s"
            + " profile=%s accent=%s",
        backendId,
        player ? "player" : "npc",
        player ? "-" : "'" + npcName + "'",
        emotion,
        race,
        gender,
        child ? "child" : "adult",
        seed < 0 ? "-" : Integer.toString(seed),
        profileName == null ? "-" : "'" + profileName + "'",
        accent == null ? "-" : "'" + accent + "'");
  }

  static String buildPlayerTrace(NpcGender gender) {
    return String.format("[TTS voice] player -> gender=%s", gender);
  }
}
