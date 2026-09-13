package com.grahambartley.runelite.voiced.dialogue.profile;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.speaker.LearnedNpcStore;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcAttributes;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcDemographicAnalyzer;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcFinder;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcLearningService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;

@Slf4j
public class VoiceManager {

  public enum PlayerVoice {
    TYPE_A(NpcGender.MALE, "Type A"),
    TYPE_B(NpcGender.FEMALE, "Type B");

    private final NpcGender gender;
    private final String label;

    PlayerVoice(NpcGender gender, String label) {
      this.gender = gender;
      this.label = label;
    }

    public NpcGender getGender() {
      return gender;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private final VoicedDialogueConfig config;
  private final NpcDemographicAnalyzer demographicAnalyzer;
  private final NpcProfileTable profileTable;
  private final NpcIdentityResolver identityResolver;
  private final NpcVoiceResolver npcVoiceResolver;

  public static VoiceManager create(VoicedDialogueConfig config, Client client) {
    NpcDemographicAnalyzer demographicAnalyzer = new NpcDemographicAnalyzer();
    demographicAnalyzer.initialize();
    NpcProfileTable profileTable = new NpcProfileTable();
    profileTable.initialize();
    return new VoiceManager(config, client, demographicAnalyzer, profileTable);
  }

  public VoiceManager(
      VoicedDialogueConfig config,
      Client client,
      NpcDemographicAnalyzer demographicAnalyzer,
      NpcProfileTable profileTable) {
    this.config = config;
    this.demographicAnalyzer = demographicAnalyzer;
    this.profileTable = profileTable;
    this.identityResolver =
        new NpcIdentityResolver(new NpcFinder(client), demographicAnalyzer, profileTable);
    this.npcVoiceResolver = new NpcVoiceResolver(config);
  }

  public void enableLearning(LearnedNpcStore store, NpcLearningService service) {
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
    VoiceSpec voice = npcVoiceResolver.resolve(npcName, identity);
    return new ResolvedSpeaker(voice, npcProfile(npcName, identity));
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

  private CharacterProfile npcProfile(String npcName, NpcIdentity identity) {
    Integer npcId = identity.worldId();
    String race = null;
    String ethnicity = null;
    NpcAttributes attributes = identity.attributes();
    if (attributes != null) {
      race = attributes.getRace();
      ethnicity = attributes.getEthnicity();
      npcId = attributes.getNpcId();
    }

    NpcProfileTable.Resolution resolution =
        profileTable.resolveNpc(npcId, identity.nameMatch(), race, ethnicity);
    if (config.debugMode()) {
      log.info(
          "[TTS profile] npc='{}' id={} race={} ethnicity={} -> '{}' (source={}, accent='{}')",
          npcName,
          npcId == null ? "MISS" : npcId,
          race == null ? "UNKNOWN" : race,
          ethnicity == null ? "-" : ethnicity,
          resolution.profile().name(),
          resolution.source(),
          resolution.profile().accent());
    }
    return resolution.profile();
  }
}
