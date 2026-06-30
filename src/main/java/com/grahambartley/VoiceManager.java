package com.grahambartley;

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
 * Resolves an NPC (or the player) to a backend-neutral {@link VoiceSpec} and, for the local Kokoro
 * backend, to a concrete Kokoro speaker id.
 *
 * <p>The spec carries the detected race and gender so the cloud backend can map them to its own
 * voice bank. The local Kokoro backend is British-only by design (issue #150): Kokoro bakes accent
 * into the chosen speaker and this is a British medieval fantasy world, so accents are a Cloud-only
 * feature. Local picks a voice from the {@link KokoroVoice} British bank by gender alone, hashing
 * each NPC into the gender pool so same-gender NPCs sound distinct but stable; race never selects a
 * Local voice. The plugin sends the chosen speaker id explicitly, so the engine just renders it.
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
   * The British Kokoro voice bank used for every Local line (British-only by design, issue #150).
   * Kokoro has no accent control, so in this British medieval fantasy world every voice is British
   * and accents are reserved for the cloud backend. The bank is small and shared across all races:
   * race never picks a Local voice, only gender does, with per-NPC variety coming from hashing into
   * the gender pool. The ids are the engine model's own speaker indices, sent on the wire verbatim.
   */
  public enum KokoroVoice {
    BM_DANIEL(24, "bm_daniel", NPCGender.MALE),
    BM_FABLE(25, "bm_fable", NPCGender.MALE),
    BM_GEORGE(26, "bm_george", NPCGender.MALE),
    BM_LEWIS(27, "bm_lewis", NPCGender.MALE),
    BF_ALICE(20, "bf_alice", NPCGender.FEMALE),
    BF_EMMA(21, "bf_emma", NPCGender.FEMALE),
    BF_ISABELLA(22, "bf_isabella", NPCGender.FEMALE);

    private final int speakerId;
    private final String voiceName;
    private final NPCGender gender;

    KokoroVoice(int speakerId, String voiceName, NPCGender gender) {
      this.speakerId = speakerId;
      this.voiceName = voiceName;
      this.gender = gender;
    }

    /** The Kokoro speaker id sent on the wire and rendered by the engine. */
    public int getSpeakerId() {
      return speakerId;
    }

    /** The underlying Kokoro voice name (for docs and logs). */
    public String getVoiceName() {
      return voiceName;
    }

    /** The gender this voice belongs to. */
    public NPCGender getGender() {
      return gender;
    }

    /** The voice name for a speaker id, or a bare {@code "id=<n>"} when outside the bank. */
    static String nameFor(int speakerId) {
      for (KokoroVoice voice : values()) {
        if (voice.speakerId == speakerId) {
          return voice.voiceName;
        }
      }
      return "id=" + speakerId;
    }
  }

  /**
   * The two selectable player voices, kept deliberately opaque ("Type A" / "Type B") so the config
   * exposes a simple either/or. Each just fixes the player's gender, which then drives the British
   * Local voice and the cloud voice.
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

    /** The gender this player voice fixes for voice resolution on both backends. */
    public NPCGender getGender() {
      return gender;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  /**
   * Gender-appropriate British speaker pools for per-NPC voice variety (issue #78). Built once from
   * the {@link KokoroVoice} bank, sorted ascending so a given NPC always hashes to the same slot
   * across runs. Selection is by gender alone; a male NPC can only ever map to a male voice and a
   * female NPC to a female voice.
   */
  static final int[] MALE_SPEAKER_POOL = poolFor(NPCGender.MALE);

  static final int[] FEMALE_SPEAKER_POOL = poolFor(NPCGender.FEMALE);

  /** The British voice the player resolves to, by gender. */
  private static final int PLAYER_MALE_SPEAKER = KokoroVoice.BM_GEORGE.getSpeakerId();

  private static final int PLAYER_FEMALE_SPEAKER = KokoroVoice.BF_EMMA.getSpeakerId();

  private static int[] poolFor(NPCGender gender) {
    java.util.TreeSet<Integer> ids = new java.util.TreeSet<>();
    for (KokoroVoice voice : KokoroVoice.values()) {
      if (voice.getGender() == gender) {
        ids.add(voice.getSpeakerId());
      }
    }
    int[] pool = new int[ids.size()];
    int i = 0;
    for (int id : ids) {
      pool[i++] = id;
    }
    return pool;
  }

  /** The British voice the player speaks with, fixed by gender. */
  static int playerSpeaker(NPCGender gender) {
    return gender == NPCGender.FEMALE ? PLAYER_FEMALE_SPEAKER : PLAYER_MALE_SPEAKER;
  }

  private final TTSDialogueConfig config;
  private final Client client;
  private final NPCDemographicAnalyzer demographicAnalyzer;
  private final NpcProfileTable profileTable;

  /** Optional runtime wiki fallback for NPCs missing from the bundled table; null when off. */
  private NpcLearningService learningService;

  public VoiceManager(TTSDialogueConfig config, Client client) {
    this.config = config;
    this.client = client;
    this.demographicAnalyzer = new NPCDemographicAnalyzer();
    this.demographicAnalyzer.initialize();
    this.profileTable = new NpcProfileTable();
    this.profileTable.initialize();
  }

  /**
   * Wires in the runtime "learn a new NPC" fallback: the analyzer consults {@code store} for NPCs
   * missing from the bundled table, and an unknown NPC triggers a one-off background wiki lookup
   * via {@code service} that populates {@code store} for subsequent lines.
   */
  public void enableLearning(LearnedNpcStore store, NpcLearningService service) {
    this.demographicAnalyzer.setLearnedStore(store);
    this.learningService = service;
  }

  /**
   * Resolves the {@link CharacterProfile} steering a line's delivery: the player's configured
   * profile for player lines, or the NPC profile built by combining every matching layer (default,
   * race, every keyword category that matches, and any per-NPC override) keyed on the NPC's
   * composition id and display name. Never returns {@code null}. Only the cloud backend renders the
   * profile; the local backend ignores it.
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
    NPC npc = findNPCByName(npcName);
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
   * of the configured player voice; an NPC uses its detected race and gender. Both carry an
   * explicit British Kokoro speaker so the local engine renders that exact voice; the cloud backend
   * reads the race/gender and uses the speaker id only as a per-NPC variety seed.
   */
  public VoiceSpec resolveVoice(String speaker, String npcName) {
    if (SPEAKER_PLAYER.equalsIgnoreCase(speaker)) {
      NPCGender gender = playerGender();
      if (config != null && config.debugMode()) {
        log.info(buildPlayerTrace(gender));
      }
      return VoiceSpec.player(gender, playerSpeaker(gender));
    }
    NpcVoice resolved = resolveNpcVoice(npcName);
    return VoiceSpec.npc(resolved.race, resolved.gender, resolved.speakerId);
  }

  /**
   * The Kokoro speaker id for a resolved {@link VoiceSpec}, used only by the local Kokoro backend.
   * The player resolves to its gender's British voice; an NPC keeps the explicit speaker it was
   * stamped with, or falls back to a gender-correct British pool pick for a bare spec.
   */
  public int kokoroSpeakerId(VoiceSpec voice) {
    if (voice.player()) {
      return playerSpeaker(voice.gender());
    }
    if (voice.hasExplicitKokoroSpeakerId()) {
      return voice.kokoroSpeakerId();
    }
    return pickNpcSpeakerId(voice.gender(), null, null);
  }

  /**
   * Picks a stable, gender-correct British speaker for a specific NPC (issue #78). NPCs of the same
   * gender are spread across the gender pool by hashing a per-NPC key into the pool index.
   *
   * <p>Keying rule (documented contract): the NPC's composition id is preferred (the same NPC type
   * always hashes the same way, regardless of how its name was presented); when no id is available
   * the normalised name is the fallback key. The pool index is {@code Math.floorMod(hash, size)} so
   * it is non-negative and deterministic. An unknown gender uses the male pool, mirroring detection
   * defaulting an unknown gender to male.
   */
  static int pickNpcSpeakerId(NPCGender gender, Integer npcId, String npcName) {
    int[] pool = gender == NPCGender.FEMALE ? FEMALE_SPEAKER_POOL : MALE_SPEAKER_POOL;
    int hash = npcId != null ? Integer.hashCode(npcId) : normalizeName(npcName).hashCode();
    return pool[Math.floorMod(hash, pool.length)];
  }

  /** Gender implied by the configured player voice. */
  private NPCGender playerGender() {
    return config.playerVoice().getGender();
  }

  /** An NPC's detected race and gender plus the British speaker chosen from the gender pool. */
  private static final class NpcVoice {
    final NPCRace race;
    final NPCGender gender;
    final int speakerId;

    NpcVoice(NPCRace race, NPCGender gender, int speakerId) {
      this.race = race;
      this.gender = gender;
      this.speakerId = speakerId;
    }
  }

  /**
   * Resolves an NPC to the race and gender carried on its spec and a stable, gender-correct per-NPC
   * British speaker id. Detection failures voice as the default human male, preserving the cloud
   * fallback voice; the Local speaker depends only on gender. Emits the debug trace once with the
   * actual chosen speaker so QA can see distinct NPCs getting distinct ids.
   */
  private NpcVoice resolveNpcVoice(String npcName) {
    if (npcName == null || npcName.isEmpty()) {
      return defaultNpcVoice(npcName, null, "blank-name");
    }

    NPC npc = findNPCByName(npcName);
    if (npc == null) {
      return defaultNpcVoice(npcName, null, "not-in-world");
    }

    NPCAttributes attributes = demographicAnalyzer.analyzeNPC(npc);
    if (attributes == null) {
      return defaultNpcVoice(npcName, npc.getId(), "analysis-failed");
    }

    NPCRace race = convertToNPCRace(attributes.getRace());
    NPCGender gender = convertToNPCGender(attributes.getGender());
    String source = "StaticTable".equals(attributes.getSource()) ? "table-hit" : "table-miss";

    // An NPC unknown to the bundled table (and the learned cache) triggers a one-off background
    // wiki lookup, so the next line voices it correctly. No-op when learning is off.
    if (race == NPCRace.UNKNOWN && learningService != null) {
      learningService.considerLearning(npc.getId(), npcName);
    }

    // The spec carries the voice categories the cloud backend reads: an unrecognised race speaks as
    // human and an unknown gender as male, matching the long-standing default. Local ignores race
    // and picks purely on gender.
    NPCRace voiceRace = race == NPCRace.UNKNOWN ? NPCRace.HUMAN : race;
    NPCGender voiceGender = gender == NPCGender.FEMALE ? NPCGender.FEMALE : NPCGender.MALE;
    int speakerId = pickNpcSpeakerId(voiceGender, npc.getId(), npcName);
    if (config != null && config.debugMode()) {
      log.info(buildNpcTrace(npcName, npc.getId(), race, gender, source, speakerId));
    }
    return new NpcVoice(voiceRace, voiceGender, speakerId);
  }

  /**
   * The default voice for an NPC whose race/gender could not be detected: the human male the cloud
   * backend has always fallen back to, with a stable Local speaker keyed off the id or name. The
   * trace records the detection miss while the spec carries the human-male voice categories.
   */
  private NpcVoice defaultNpcVoice(String npcName, Integer npcId, String source) {
    int speakerId = pickNpcSpeakerId(NPCGender.MALE, npcId, npcName);
    if (config != null && config.debugMode()) {
      log.info(
          buildNpcTrace(npcName, npcId, NPCRace.UNKNOWN, NPCGender.UNKNOWN, source, speakerId));
    }
    return new NpcVoice(NPCRace.HUMAN, NPCGender.MALE, speakerId);
  }

  /**
   * Builds the NPC voice-resolution trace string. Factored out (and package-private) so it is
   * unit-testable without a live client or logger. It exposes the whole resolution path (world
   * hit/id, table hit/miss, detected race/gender + source) and the actual chosen British speaker
   * id/name that will be synthesized.
   */
  static String buildNpcTrace(
      String npcName,
      Integer npcId,
      NPCRace race,
      NPCGender gender,
      String source,
      int chosenSpeakerId) {
    return String.format(
        "[TTS voice] npc='%s' world=%s race=%s gender=%s source=%s -> speaker=%s(speakerId=%d)",
        npcName,
        npcId == null ? "MISS" : "HIT(id=" + npcId + ")",
        race == null ? "UNKNOWN" : race,
        gender,
        source,
        KokoroVoice.nameFor(chosenSpeakerId),
        chosenSpeakerId);
  }

  /** The Kokoro voice name for a speaker id, or a bare {@code "id=<n>"} when outside the bank. */
  static String kokoroVoiceName(int speakerId) {
    return KokoroVoice.nameFor(speakerId);
  }

  /** Builds the player voice-resolution trace string. Package-private for unit testing. */
  static String buildPlayerTrace(NPCGender gender) {
    int speakerId = playerSpeaker(gender);
    return String.format(
        "[TTS voice] player -> speaker=%s(speakerId=%d) gender=%s",
        KokoroVoice.nameFor(speakerId), speakerId, gender);
  }

  /**
   * Find NPC entity by name in the current game world. Matching is tolerant of presentation
   * differences between the dialogue name widget (which can carry {@code <col=...>} markup,
   * non-breaking spaces, and casing) and the raw composition name: both sides are stripped of any
   * {@code <...>} tags, have non-breaking spaces normalised, are trimmed, and compared
   * case-insensitively. This stops cosmetic markup from forcing a false miss and the default voice.
   */
  private NPC findNPCByName(String targetName) {
    if (client == null || client.getNpcs() == null) {
      return null;
    }

    String wanted = normalizeName(targetName);
    if (wanted.isEmpty()) {
      return null;
    }

    return client.getNpcs().stream()
        .filter(npc -> npc != null && npc.getName() != null)
        .filter(npc -> normalizeName(npc.getName()).equalsIgnoreCase(wanted))
        .findFirst()
        .orElse(null);
  }

  /**
   * Normalises an NPC name for tolerant matching: strips any {@code <...>} tags, converts
   * non-breaking spaces to regular spaces, and trims. Case is handled by the caller's comparison.
   * Package-private for unit testing.
   */
  static String normalizeName(String name) {
    if (name == null) {
      return "";
    }
    return name.replaceAll("<[^>]*>", "").replace(' ', ' ').trim();
  }

  /** Convert string race attribute to NPCRace enum. */
  private NPCRace convertToNPCRace(String race) {
    if (race == null || race.isEmpty()) {
      return NPCRace.UNKNOWN;
    }

    try {
      return NPCRace.valueOf(race.toUpperCase());
    } catch (IllegalArgumentException e) {
      // Handle mappings for races not directly in our enum
      String raceLower = race.toLowerCase();

      if (raceLower.contains("human")
          || raceLower.contains("man")
          || raceLower.contains("person")) {
        return NPCRace.HUMAN;
      } else if (raceLower.contains("elf") || raceLower.contains("elven")) {
        return NPCRace.ELF;
      } else if (raceLower.contains("dwarf") || raceLower.contains("dwarven")) {
        return NPCRace.DWARF;
      } else if (raceLower.contains("goblin") || raceLower.contains("gnome")) {
        return NPCRace.GOBLIN;
      } else if (raceLower.contains("troll") || raceLower.contains("giant")) {
        return NPCRace.TROLL;
      } else if (raceLower.contains("undead")
          || raceLower.contains("skeleton")
          || raceLower.contains("zombie")
          || raceLower.contains("ghost")) {
        return NPCRace.UNDEAD;
      } else if (raceLower.contains("demon")
          || raceLower.contains("dragon")
          || raceLower.contains("devil")) {
        return NPCRace.DEMON;
      } else if (raceLower.contains("gorilla")) {
        return NPCRace.GORILLA;
      } else if (raceLower.contains("monkey") || raceLower.contains("primate")) {
        return NPCRace.MONKEY;
      } else if (raceLower.contains("wizard") || raceLower.contains("mage")) {
        return NPCRace.WIZARD;
      } else if (raceLower.contains("tortugan") || raceLower.contains("tortuga")) {
        return NPCRace.TORTUGAN;
      }

      log.debug("Unknown race '{}', using default voice", race);
      return NPCRace.UNKNOWN;
    }
  }

  /** Convert string gender attribute to NPCGender enum. */
  private NPCGender convertToNPCGender(String gender) {
    if (gender == null || gender.isEmpty()) {
      return NPCGender.UNKNOWN;
    }

    try {
      return NPCGender.valueOf(gender.toUpperCase());
    } catch (IllegalArgumentException e) {
      // Handle various gender representations
      String genderLower = gender.toLowerCase();

      if (genderLower.contains("female")
          || genderLower.contains("woman")
          || genderLower.contains("girl")
          || genderLower.contains("lady")) {
        return NPCGender.FEMALE;
      } else if (genderLower.contains("male")
          || genderLower.contains("man")
          || genderLower.contains("boy")
          || genderLower.contains("lord")) {
        return NPCGender.MALE;
      }

      log.debug("Unknown gender '{}', defaulting to MALE", gender);
      return NPCGender.MALE;
    }
  }
}
