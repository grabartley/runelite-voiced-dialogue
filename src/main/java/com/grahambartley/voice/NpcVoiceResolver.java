package com.grahambartley.voice;

import com.grahambartley.VoicedDialogueConfig;
import com.grahambartley.data.NPCAttributes;
import com.grahambartley.data.NPCDemographicAnalyzer;
import com.grahambartley.data.NpcLearningService;
import com.grahambartley.synthesis.VoiceSpec;
import com.grahambartley.voice.VoiceManager.NPCGender;
import com.grahambartley.voice.VoiceManager.NPCRace;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;

/**
 * Resolves an NPC name to a backend-neutral {@link VoiceSpec}: its detected race and gender plus a
 * stable per-NPC variety seed. Detection failures voice as the default human male (preserving the
 * long-standing cloud fallback). An NPC unknown to the bundled table (and the learned cache)
 * triggers a one-off background wiki lookup so the next line voices it correctly. Emits the debug
 * trace once.
 */
@Slf4j
final class NpcVoiceResolver {

  private final VoicedDialogueConfig config;
  private final NPCDemographicAnalyzer demographicAnalyzer;
  private final NpcFinder npcFinder;

  /** Optional runtime wiki fallback for NPCs missing from the bundled table; null when off. */
  private NpcLearningService learningService;

  NpcVoiceResolver(
      VoicedDialogueConfig config,
      NPCDemographicAnalyzer demographicAnalyzer,
      NpcFinder npcFinder) {
    this.config = config;
    this.demographicAnalyzer = demographicAnalyzer;
    this.npcFinder = npcFinder;
  }

  void setLearningService(NpcLearningService learningService) {
    this.learningService = learningService;
  }

  VoiceSpec resolve(String npcName) {
    if (npcName == null || npcName.isEmpty()) {
      return defaultVoice(npcName, null, "blank-name");
    }

    NPC npc = npcFinder.findByName(npcName);
    if (npc == null) {
      return defaultVoice(npcName, null, "not-in-world");
    }

    NPCAttributes attributes = demographicAnalyzer.analyzeNPC(npc);
    if (attributes == null) {
      return defaultVoice(npcName, npc.getId(), "analysis-failed");
    }

    NPCRace race = NpcDemographicParser.toRace(attributes.getRace());
    NPCGender gender = NpcDemographicParser.toGender(attributes.getGender());
    String source = "StaticTable".equals(attributes.getSource()) ? "table-hit" : "table-miss";

    // An NPC unknown to the bundled table (and the learned cache) triggers a one-off background
    // wiki lookup, so the next line voices it correctly. No-op when learning is off.
    if (race == NPCRace.UNKNOWN && learningService != null) {
      learningService.considerLearning(npc.getId(), npcName);
    }

    // The spec carries the voice categories the cloud backend reads: an unrecognised race speaks as
    // human and an unknown gender as male, matching the long-standing default.
    NPCRace voiceRace = race == NPCRace.UNKNOWN ? NPCRace.HUMAN : race;
    NPCGender voiceGender = gender == NPCGender.FEMALE ? NPCGender.FEMALE : NPCGender.MALE;
    int seed = voiceSeed(npc.getId(), npcName);
    if (config != null && config.debugMode()) {
      log.info(VoiceTraceFormatter.buildNpcTrace(npcName, npc.getId(), race, gender, source, seed));
    }
    return VoiceSpec.npc(voiceRace, voiceGender, seed);
  }

  /**
   * The default voice for an NPC whose race/gender could not be detected: the human male the cloud
   * backend has always fallen back to, with a stable per-NPC variety seed keyed off the id or name.
   */
  private VoiceSpec defaultVoice(String npcName, Integer npcId, String source) {
    int seed = voiceSeed(npcId, npcName);
    if (config != null && config.debugMode()) {
      log.info(
          VoiceTraceFormatter.buildNpcTrace(
              npcName, npcId, NPCRace.UNKNOWN, NPCGender.UNKNOWN, source, seed));
    }
    return VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE, seed);
  }

  /**
   * A stable, non-negative per-NPC variety seed (#78). The composition id is preferred so the same
   * NPC type always resolves the same voice regardless of how its name was presented; the
   * normalised name is the fallback key. Kept non-negative so it is never treated as an absent
   * seed.
   */
  private static int voiceSeed(Integer npcId, String npcName) {
    int hash =
        npcId != null ? Integer.hashCode(npcId) : NameNormalizer.normalize(npcName).hashCode();
    return hash & Integer.MAX_VALUE;
  }
}
