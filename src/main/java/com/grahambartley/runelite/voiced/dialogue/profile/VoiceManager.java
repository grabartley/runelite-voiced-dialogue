package com.grahambartley.runelite.voiced.dialogue.profile;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.speaker.LearnedNpcStore;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcAttributes;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcDemographicAnalyzer;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcFinder;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.wiki.NpcLearningService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;

@Slf4j
public class VoiceManager {

  private final VoicedDialogueConfig config;
  private final NpcDemographicAnalyzer demographicAnalyzer;
  private final NpcProfileTable profileTable;
  private final NpcIdentityResolver identityResolver;
  private final NpcVoiceResolver npcVoiceResolver;
  private final NpcVoiceOverrideStore overrideStore;

  private NpcLearningService learningService;

  public static VoiceManager create(
      VoicedDialogueConfig config, Client client, NpcVoiceOverrideStore overrideStore) {
    NpcDemographicAnalyzer demographicAnalyzer = new NpcDemographicAnalyzer();
    demographicAnalyzer.initialize();
    NpcProfileTable profileTable = new NpcProfileTable();
    profileTable.initialize();
    return new VoiceManager(config, client, demographicAnalyzer, profileTable, overrideStore);
  }

  public VoiceManager(
      VoicedDialogueConfig config,
      Client client,
      NpcDemographicAnalyzer demographicAnalyzer,
      NpcProfileTable profileTable,
      NpcVoiceOverrideStore overrideStore) {
    this.config = config;
    this.demographicAnalyzer = demographicAnalyzer;
    this.profileTable = profileTable;
    this.overrideStore = overrideStore;
    this.identityResolver =
        new NpcIdentityResolver(new NpcFinder(client), demographicAnalyzer, profileTable);
    this.npcVoiceResolver = new NpcVoiceResolver(config);
  }

  public boolean isVoiced(int npcId) {
    return demographicAnalyzer.isVoiced(npcId);
  }

  public void offerToLearning(String menuOption, NPC npc) {
    if (learningService == null
        || npc == null
        || !learningService.isEnabled()
        || !learningService.startsConversation(menuOption)) {
      return;
    }
    NpcAttributes attributes = demographicAnalyzer.analyzeNPC(npc);
    if (attributes != null) {
      learningService.considerLearning(attributes.getNpcId(), npc.getName());
    }
  }

  public void enableLearning(LearnedNpcStore store, NpcLearningService service) {
    this.learningService = service;
    this.demographicAnalyzer.setLearnedStore(store);
    this.npcVoiceResolver.setLearningService(service);
  }

  public ResolvedSpeaker resolve(Speaker speaker, String npcName) {
    if (speaker == Speaker.PLAYER) {
      return new ResolvedSpeaker(playerVoice(), playerProfile());
    }

    return npcSpeaker(npcName, identityResolver.resolve(npcName));
  }

  public ResolvedSpeaker resolveNpc(NPC npc) {
    return npcSpeaker(npc.getName(), identityResolver.resolve(npc));
  }

  private ResolvedSpeaker npcSpeaker(String npcName, NpcIdentity identity) {
    NpcVoiceOverride override = overrideStore.get(identity.profileId());
    VoiceSpec voice =
        npcVoiceResolver.resolve(npcName, identity, override == null ? null : override.voiceType());
    return new ResolvedSpeaker(voice, npcProfile(npcName, identity, override));
  }

  public ResolvedSpeaker resolveNarrator() {
    return new ResolvedSpeaker(VoiceSpec.NARRATOR, profileTable.resolveNarrator());
  }

  private VoiceSpec playerVoice() {
    NpcGender gender = config.playerVoice().getGender();
    if (config.debugMode()) {
      log.info(VoiceTraceFormatter.buildPlayerTrace(gender));
    }
    return VoiceSpec.player(gender);
  }

  private CharacterProfile playerProfile() {
    CharacterProfile profile =
        profileTable.resolvePlayer(
            config.playerAccent(), config.playerPersona(), config.playerPace());
    if (config.debugMode()) {
      log.info("[TTS profile] player -> '{}' accent='{}'", profile.name(), profile.accent());
    }
    return profile;
  }

  private CharacterProfile npcProfile(
      String npcName, NpcIdentity identity, NpcVoiceOverride override) {
    Integer npcId = identity.profileId();
    String race = null;
    String ethnicity = null;
    NpcAttributes attributes = identity.attributes();
    if (attributes != null) {
      race = attributes.getRace();
      ethnicity = attributes.getEthnicity();
    }

    NpcProfileTable.Resolution resolution =
        profileTable.resolveNpc(
            npcId, identity.nameMatch(), race, ethnicity, identity.child(), override);
    if (config.debugMode()) {
      log.info(
          "[TTS profile] npc='{}' id={} race={} ethnicity={} -> '{}' (source={}, accent='{}',"
              + " voiceRegion={})",
          npcName,
          npcId == null ? "MISS" : npcId,
          race == null ? "UNKNOWN" : race,
          ethnicity == null ? "-" : ethnicity,
          resolution.profile().name(),
          resolution.source(),
          resolution.profile().accent(),
          resolution.profile().voiceRegion() == null ? "-" : resolution.profile().voiceRegion());
    }
    return resolution.profile();
  }
}
