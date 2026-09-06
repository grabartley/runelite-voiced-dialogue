package com.grahambartley.runelite.voiced.dialogue.profile;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.speaker.AttributeSource;
import com.grahambartley.runelite.voiced.dialogue.speaker.NameNormalizer;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcAttributes;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcDemographicParser;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcLearningService;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns an NPC's resolved {@link NpcIdentity} into a backend-neutral {@link VoiceSpec}: its
 * detected race and gender, a stable per-NPC variety seed, and whether the NPC is a child (from the
 * bundled table's life-stage marker or a child keyword category on the display name). Unknowns
 * voice as the default human male. An NPC unknown to the bundled table (and the learned cache)
 * triggers a one-off background wiki lookup so the next line voices it correctly. Emits the debug
 * trace once.
 */
@Slf4j
final class NpcVoiceResolver {

  private final VoicedDialogueConfig config;

  /** Optional runtime wiki fallback for NPCs missing from the bundled table; null when off. */
  private NpcLearningService learningService;

  NpcVoiceResolver(VoicedDialogueConfig config) {
    this.config = config;
  }

  void setLearningService(NpcLearningService learningService) {
    this.learningService = learningService;
  }

  VoiceSpec resolve(String npcName, NpcIdentity identity) {
    if (npcName == null || npcName.isEmpty()) {
      return defaultVoice(npcName, null, identity, "blank-name");
    }
    if (identity.worldId() == null) {
      return defaultVoice(npcName, null, identity, "not-in-world");
    }
    NpcAttributes attributes = identity.attributes();
    if (attributes == null) {
      return defaultVoice(npcName, identity.worldId(), identity, "analysis-failed");
    }

    NpcRace race = NpcDemographicParser.toRace(attributes.getRace());
    NpcGender gender = NpcDemographicParser.toGender(attributes.getGender());
    String source =
        AttributeSource.STATIC_TABLE.equals(attributes.getSource()) ? "table-hit" : "table-miss";

    if (race == NpcRace.UNKNOWN && learningService != null) {
      learningService.considerLearning(identity.worldId(), npcName);
    }

    NpcRace voiceRace = race == NpcRace.UNKNOWN ? NpcRace.HUMAN : race;
    NpcGender voiceGender = NpcDemographicParser.toVoiceGender(gender);
    boolean child = attributes.isChild() || identity.nameMatch().child();
    int seed = voiceSeed(identity.worldId(), npcName);
    if (config.debugMode()) {
      log.info(
          VoiceTraceFormatter.buildNpcTrace(
              npcName, identity.worldId(), race, gender, child, source, seed));
    }
    return VoiceSpec.npc(voiceRace, voiceGender, seed, child);
  }

  /**
   * The default voice for an NPC whose race/gender could not be detected: the default human male,
   * with a stable per-NPC variety seed keyed off the id or name.
   */
  private VoiceSpec defaultVoice(
      String npcName, Integer npcId, NpcIdentity identity, String source) {
    // A child-named NPC keeps its youthful voice even when race/gender detection failed.
    boolean child = identity.nameMatch().child();
    int seed = voiceSeed(npcId, npcName);
    if (config.debugMode()) {
      log.info(
          VoiceTraceFormatter.buildNpcTrace(
              npcName, npcId, NpcRace.UNKNOWN, NpcGender.UNKNOWN, child, source, seed));
    }
    return VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, seed, child);
  }

  /**
   * A stable, non-negative per-NPC variety seed. The composition id is preferred so the same NPC
   * type always resolves the same voice regardless of how its name was presented; the normalised
   * name is the fallback key. Kept non-negative so it is never treated as an absent seed.
   */
  private static int voiceSeed(Integer npcId, String npcName) {
    int hash =
        npcId != null ? Integer.hashCode(npcId) : NameNormalizer.normalize(npcName).hashCode();
    return hash & Integer.MAX_VALUE;
  }
}
