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

  VoiceSpec resolve(String npcName, NpcIdentity identity, VoiceType voiceType) {
    if (npcName == null || npcName.isEmpty()) {
      return defaultVoice(npcName, null, identity, voiceType, "blank-name");
    }
    if (identity.worldId() == null) {
      return defaultVoice(npcName, null, identity, voiceType, "not-in-world");
    }
    NpcAttributes attributes = identity.attributes();
    if (attributes == null) {
      return defaultVoice(npcName, identity.seedId(), identity, voiceType, "analysis-failed");
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
    if (voiceType != null) {
      voiceGender = voiceType.getGender();
      gender = voiceGender;
      source += "+voice-type-override";
    }
    boolean child = identity.child();
    int seed = voiceSeed(identity.seedId(), npcName);
    if (config.debugMode()) {
      log.info(
          VoiceTraceFormatter.buildNpcTrace(
              npcName, identity.worldId(), race, gender, child, source, seed));
    }
    return VoiceSpec.npc(voiceRace, voiceGender, seed, child);
  }

  private VoiceSpec defaultVoice(
      String npcName, Integer npcId, NpcIdentity identity, VoiceType voiceType, String source) {
    boolean child = identity.child();
    int seed = voiceSeed(npcId, npcName);
    NpcGender voiceTypeGender = voiceType == null ? null : voiceType.getGender();
    if (voiceTypeGender != null) {
      source += "+voice-type-override";
    }
    if (config.debugMode()) {
      log.info(
          VoiceTraceFormatter.buildNpcTrace(
              npcName,
              npcId,
              NpcRace.UNKNOWN,
              voiceTypeGender == null ? NpcGender.UNKNOWN : voiceTypeGender,
              child,
              source,
              seed));
    }
    return VoiceSpec.npc(
        NpcRace.HUMAN, voiceTypeGender == null ? NpcGender.MALE : voiceTypeGender, seed, child);
  }

  private static int voiceSeed(Integer npcId, String npcName) {
    int hash =
        npcId != null ? Integer.hashCode(npcId) : NameNormalizer.normalize(npcName).hashCode();
    return hash & Integer.MAX_VALUE;
  }
}
