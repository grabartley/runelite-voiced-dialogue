package com.grahambartley.runelite.voiced.dialogue.profile;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.speaker.AttributeSource;
import com.grahambartley.runelite.voiced.dialogue.speaker.NameNormalizer;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcAttributes;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcDemographicParser;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import com.grahambartley.runelite.voiced.dialogue.speaker.wiki.NpcLearningService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class NpcVoiceResolver {

  private final VoicedDialogueConfig config;

  private NpcLearningService learningService;

  NpcVoiceResolver(VoicedDialogueConfig config) {
    this.config = config;
  }

  void setLearningService(NpcLearningService learningService) {
    this.learningService = learningService;
  }

  VoiceSpec resolve(String npcName, NpcIdentity identity, NpcGender overriddenGender) {
    if (npcName == null || npcName.isEmpty()) {
      return defaultVoice(npcName, null, identity, overriddenGender, "blank-name");
    }
    if (identity.worldId() == null) {
      return defaultVoice(npcName, null, identity, overriddenGender, "not-in-world");
    }
    NpcAttributes attributes = identity.attributes();
    if (attributes == null) {
      return defaultVoice(
          npcName, identity.baseId(), identity, overriddenGender, "analysis-failed");
    }

    NpcRace race = NpcDemographicParser.toRace(attributes.getRace());
    NpcGender gender = NpcDemographicParser.toGender(attributes.getGender());
    String source =
        AttributeSource.STATIC_TABLE.equals(attributes.getSource()) ? "table-hit" : "table-miss";

    if (learningService != null) {
      learningService.considerLearning(attributes.getNpcId(), npcName);
    }

    NpcRace voiceRace = race == NpcRace.UNKNOWN ? NpcRace.HUMAN : race;
    NpcGender voiceGender = NpcDemographicParser.toVoiceGender(gender);
    if (overriddenGender != null) {
      voiceGender = overriddenGender;
      gender = overriddenGender;
      source += "+gender-override";
    }
    boolean child = identity.child();
    int seed = voiceSeed(identity.baseId(), npcName);
    if (config.debugMode()) {
      log.info(
          VoiceTraceFormatter.buildNpcTrace(
              npcName, identity.worldId(), race, gender, child, source, seed));
    }
    return VoiceSpec.npc(voiceRace, voiceGender, seed, child);
  }

  private VoiceSpec defaultVoice(
      String npcName,
      Integer npcId,
      NpcIdentity identity,
      NpcGender overriddenGender,
      String source) {
    boolean child = identity.child();
    int seed = voiceSeed(npcId, npcName);
    if (overriddenGender != null) {
      source += "+gender-override";
    }
    if (config.debugMode()) {
      log.info(
          VoiceTraceFormatter.buildNpcTrace(
              npcName,
              npcId,
              NpcRace.UNKNOWN,
              overriddenGender == null ? NpcGender.UNKNOWN : overriddenGender,
              child,
              source,
              seed));
    }
    return VoiceSpec.npc(
        NpcRace.HUMAN, overriddenGender == null ? NpcGender.MALE : overriddenGender, seed, child);
  }

  private static int voiceSeed(Integer npcId, String npcName) {
    int hash =
        npcId != null ? Integer.hashCode(npcId) : NameNormalizer.normalize(npcName).hashCode();
    return hash & Integer.MAX_VALUE;
  }
}
