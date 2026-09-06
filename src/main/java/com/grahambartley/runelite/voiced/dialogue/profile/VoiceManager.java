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

/**
 * Resolves an NPC (or the player) to a backend-neutral {@link VoiceSpec} and the per-speaker {@link
 * CharacterProfile}. A thin facade over focused collaborators: NPC identity ({@link
 * NpcIdentityResolver}), voice resolution ({@link NpcVoiceResolver}), profile layering ({@link
 * NpcProfileTable}), and trace formatting ({@link VoiceTraceFormatter}).
 *
 * <p>The spec carries the detected race and gender so the cloud backend can map them to its own
 * voice bank, plus a stable per-NPC variety seed so same-race/gender NPCs are spread across a
 * sub-pool and sound distinct.
 */
@Slf4j
public class VoiceManager {

  /**
   * The two selectable player voices, kept deliberately opaque ("Type A" / "Type B") so the config
   * exposes a simple either/or. Each just fixes the player's gender, which then drives the cloud
   * voice.
   */
  public enum PlayerVoice {
    TYPE_A(NpcGender.MALE, "Type A"),
    TYPE_B(NpcGender.FEMALE, "Type B");

    private final NpcGender gender;
    private final String label;

    PlayerVoice(NpcGender gender, String label) {
      this.gender = gender;
      this.label = label;
    }

    /** The gender this player voice fixes for cloud voice resolution. */
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

  /** Builds a manager over freshly loaded copies of both bundled tables. */
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

  /**
   * Wires in the runtime "learn a new NPC" fallback: the analyzer consults {@code store} for NPCs
   * missing from the bundled table, and an unknown NPC triggers a one-off background wiki lookup
   * via {@code service} that populates {@code store} for subsequent lines.
   */
  public void enableLearning(LearnedNpcStore store, NpcLearningService service) {
    this.demographicAnalyzer.setLearnedStore(store);
    this.npcVoiceResolver.setLearningService(service);
  }

  /**
   * Resolves who is speaking a line, once: the NPC behind the name is looked up a single time and
   * both the voice and the profile are derived from that one result. The player uses the gender of
   * the configured player voice and their configured profile; an NPC uses its detected race and
   * gender plus a stable per-NPC variety seed, and the profile built by combining every matching
   * layer (default, race, ethnicity, every keyword category that matches, and any per-NPC
   * override).
   *
   * <p>The profile is {@code null} when character profiles are switched off, which keeps the
   * request and its synthesis cache key identical to what a profile-free resolution produces.
   */
  public ResolvedSpeaker resolve(Speaker speaker, String npcName) {
    boolean withProfile = config.cloudCharacterProfiles();
    if (speaker == Speaker.PLAYER) {
      return new ResolvedSpeaker(playerVoice(), withProfile ? playerProfile() : null);
    }

    NpcIdentity identity = identityResolver.resolve(npcName);
    VoiceSpec voice = npcVoiceResolver.resolve(npcName, identity);
    return new ResolvedSpeaker(voice, withProfile ? npcProfile(npcName, identity) : null);
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
      // The id the analyzer actually matched (active or base), so a bespoke byId profile keyed by
      // the wiki id resolves even for transformed multiloc NPCs.
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
