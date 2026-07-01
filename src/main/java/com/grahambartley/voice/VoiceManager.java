package com.grahambartley.voice;

import com.grahambartley.VoicedDialogueConfig;
import com.grahambartley.data.LearnedNpcStore;
import com.grahambartley.data.NPCAttributes;
import com.grahambartley.data.NPCDemographicAnalyzer;
import com.grahambartley.data.NpcLearningService;
import com.grahambartley.data.NpcProfileTable;
import com.grahambartley.synthesis.CharacterProfile;
import com.grahambartley.synthesis.VoiceSpec;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;

/**
 * Resolves an NPC (or the player) to a backend-neutral {@link VoiceSpec} and the per-speaker {@link
 * CharacterProfile}. A thin facade over focused collaborators: NPC lookup ({@link NpcFinder}),
 * voice resolution ({@link NpcVoiceResolver}), and trace formatting ({@link VoiceTraceFormatter}).
 *
 * <p>The spec carries the detected race and gender so the cloud backend can map them to its own
 * voice bank, plus a stable per-NPC variety seed so same-race/gender NPCs are spread across a
 * sub-pool and sound distinct (#78).
 */
@Slf4j
public class VoiceManager {

  /** Speaker-type tokens passed as the {@code speaker} argument to the resolve methods. */
  public static final String SPEAKER_PLAYER = "player";

  public static final String SPEAKER_NPC = "npc";

  public enum NPCRace {
    HUMAN,
    ELF,
    DWARF,
    GOBLIN,
    MONKEY,
    GORILLA,
    TROLL,
    UNDEAD,
    DEMON,
    WIZARD,
    TORTUGAN,
    UNKNOWN
  }

  public enum NPCGender {
    MALE,
    FEMALE,
    UNKNOWN
  }

  /**
   * The two selectable player voices, kept deliberately opaque ("Type A" / "Type B") so the config
   * exposes a simple either/or. Each just fixes the player's gender, which then drives the cloud
   * voice.
   */
  public enum PlayerVoice {
    TYPE_A(NPCGender.MALE, "Type A"),
    TYPE_B(NPCGender.FEMALE, "Type B");

    private final NPCGender gender;
    private final String label;

    PlayerVoice(NPCGender gender, String label) {
      this.gender = gender;
      this.label = label;
    }

    /** The gender this player voice fixes for cloud voice resolution. */
    public NPCGender getGender() {
      return gender;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private final VoicedDialogueConfig config;
  private final NPCDemographicAnalyzer demographicAnalyzer;
  private final NpcProfileTable profileTable;
  private final NpcFinder npcFinder;
  private final NpcVoiceResolver npcVoiceResolver;

  public VoiceManager(VoicedDialogueConfig config, Client client) {
    this.config = config;
    this.demographicAnalyzer = new NPCDemographicAnalyzer();
    this.demographicAnalyzer.initialize();
    this.profileTable = new NpcProfileTable();
    this.profileTable.initialize();
    this.npcFinder = new NpcFinder(client);
    this.npcVoiceResolver = new NpcVoiceResolver(config, demographicAnalyzer, npcFinder);
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
   * Resolves the {@link CharacterProfile} steering a line's delivery: the player's configured
   * profile for player lines, or the NPC profile built by combining every matching layer (default,
   * race, every keyword category that matches, and any per-NPC override) keyed on the NPC's
   * composition id and display name. Never returns {@code null}.
   */
  public CharacterProfile resolveProfile(String speaker, String npcName) {
    if (SPEAKER_PLAYER.equalsIgnoreCase(speaker)) {
      CharacterProfile profile =
          profileTable.resolvePlayer(
              config.playerAccent(), config.playerPersona(), config.playerPace());
      if (config.debugMode()) {
        log.info("[TTS profile] player -> '{}' accent='{}'", profile.name(), profile.accent());
      }
      return profile;
    }

    Integer npcId = null;
    String race = null;
    String ethnicity = null;
    NPC npc = npcFinder.findByName(npcName);
    if (npc != null) {
      npcId = npc.getId();
      NPCAttributes attributes = demographicAnalyzer.analyzeNPC(npc);
      if (attributes != null) {
        race = attributes.getRace();
        ethnicity = attributes.getEthnicity();
        // The id the analyzer actually matched (active or base), so a bespoke byId profile keyed by
        // the wiki id resolves even for transformed multiloc NPCs.
        npcId = attributes.getNpcId();
      }
    }

    NpcProfileTable.Resolution resolution =
        profileTable.resolveNpc(npcId, npcName, race, ethnicity);
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

  /**
   * Resolves a backend-neutral {@link VoiceSpec} for a line of dialogue. The player uses the gender
   * of the configured player voice; an NPC uses its detected race and gender plus a stable per-NPC
   * variety seed the cloud backend spreads across its voice sub-pool.
   */
  public VoiceSpec resolveVoice(String speaker, String npcName) {
    if (SPEAKER_PLAYER.equalsIgnoreCase(speaker)) {
      NPCGender gender = playerGender();
      if (config != null && config.debugMode()) {
        log.info(VoiceTraceFormatter.buildPlayerTrace(gender));
      }
      return VoiceSpec.player(gender);
    }
    return npcVoiceResolver.resolve(npcName);
  }

  /** Gender implied by the configured player voice. */
  private NPCGender playerGender() {
    return config.playerVoice().getGender();
  }
}
