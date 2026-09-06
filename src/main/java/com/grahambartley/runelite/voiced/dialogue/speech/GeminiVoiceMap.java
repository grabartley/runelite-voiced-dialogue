package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import java.util.EnumMap;
import java.util.Map;

/**
 * Maps a backend-neutral {@link VoiceSpec} onto a concrete Gemini 3.1 Flash TTS voice name.
 *
 * <p>Gemini exposes 30 prebuilt voices identified only by name and a vibe adjective (Charon is
 * "Informative", Algenib is "Gravelly", and so on); the API carries no gender metadata, so the
 * male/female split here is confirmed by ear from a generated sample pack rather than read from the
 * API. Each race/gender pair anchors to a small, gender-correct sub-pool of voices chosen for race
 * character (gravelly timbres for dwarves and trolls, refined for elves, wizards and the ascended
 * Citizens of Arceuus, light for goblins, breathy for the undead). Two NPCs of the same race and
 * gender are spread across that sub-pool by the per-NPC seed already stamped on the spec, so they
 * sound distinct but stable across sessions.
 *
 * <p>Gender-correctness is structural: a male spec can only ever resolve to a voice from a male
 * sub-pool and a female spec to a female sub-pool, so no race maps two genders onto the same voice.
 * The player respects the configured player-voice gender. {@link #UNKNOWN} race and unknown gender
 * fall back to the neutral human-male anchor so every spec resolves to a real voice.
 *
 * <p>Life stage is a third axis: a child spec (any race, any ethnicity) resolves to a dedicated
 * youthful sub-pool of its gender instead of the adult race anchor, still spread by the per-NPC
 * seed. Every child voice is drawn from the gender it already belongs to above, so the gender
 * disjointness invariant holds with children included.
 */
final class GeminiVoiceMap {

  /** Neutral default when a spec has no specific mapping (a clear, even male voice). */
  static final String DEFAULT_VOICE = "Charon";

  /** Default for a child spec whose pool is somehow empty (the upbeat child-male anchor). */
  static final String DEFAULT_CHILD_VOICE = "Puck";

  private final Map<NpcRace, Map<NpcGender, String[]>> npcVoices;
  private final Map<NpcGender, String[]> playerVoices;
  private final Map<NpcGender, String[]> childVoices;

  GeminiVoiceMap() {
    playerVoices = new EnumMap<>(NpcGender.class);
    playerVoices.put(NpcGender.MALE, new String[] {"Achird", "Iapetus"});
    playerVoices.put(NpcGender.FEMALE, new String[] {"Aoede", "Autonoe"});

    // Children of any race resolve here instead of the adult race anchor, confirmed young BY EAR,
    // which trumps the catalog vibe adjectives: Puck is the only male voice that reads as a young
    // boy (Sadachbia's "Lively" and Fenrir's "Excitable" both read adult/feminine, so they stay
    // out; boys deliberately share Puck until another candidate passes the ear test), and Leda and
    // Zephyr are the girls that both read young AND hold the directed British accent (Laomedeia
    // drifts off it, so it stays out). The childlike timbre dominates; race and accent still
    // colour the delivery through the character-profile directive text, so a troll child sounds
    // young rather than large.
    childVoices = new EnumMap<>(NpcGender.class);
    childVoices.put(NpcGender.MALE, new String[] {"Puck"});
    childVoices.put(NpcGender.FEMALE, new String[] {"Leda", "Zephyr"});

    npcVoices = new EnumMap<>(NpcRace.class);
    // Voice depth is inferred from the catalog's character adjectives: gravelly (Algenib), firm
    // (Alnilam, Orus), even (Schedar), breathy (Enceladus) and informative (Charon, Rasalgethi,
    // Sadaltager) are the deep, mature end; upbeat (Puck) and casual (Zubenelgenubi) are bright.
    // Big, imposing races (troll/ogre, demon, undead, dwarf) anchor to the deep end so they sound
    // large rather than high-pitched; goblins stay deliberately bright and small.
    // Human (most common): clear, neutral, mid-depth voices.
    put(NpcRace.HUMAN, male("Charon", "Iapetus"), female("Despina", "Erinome"));
    // Elf (refined, elegant): clear/refined voices.
    put(NpcRace.ELF, male("Iapetus", "Rasalgethi"), female("Vindemiatrix", "Erinome"));
    // Dwarf (gruff, sturdy): gravelly/firm, deep.
    put(NpcRace.DWARF, male("Algenib", "Alnilam"), female("Gacrux", "Kore"));
    // Goblin (small, crude): bright/light voices, deliberately high.
    put(NpcRace.GOBLIN, male("Puck", "Zubenelgenubi"), female("Leda", "Laomedeia"));
    // Monkey (small, quick, chattery): bright, energetic, playful.
    put(NpcRace.MONKEY, male("Fenrir", "Sadachbia"), female("Zephyr", "Pulcherrima"));
    // Gorilla (huge, booming, primal): gravelly/firm, anchored to the deepest end.
    put(NpcRace.GORILLA, male("Algenib", "Orus"), female("Gacrux", "Kore"));
    // Troll/ogre (big, lumbering): gravelly/firm, the deepest male timbres.
    put(NpcRace.TROLL, male("Algenib", "Orus"), female("Gacrux", "Kore"));
    // Undead (hollow, eerie): breathy/even, deep and cold.
    put(NpcRace.UNDEAD, male("Enceladus", "Schedar"), female("Achernar", "Sulafat"));
    // Demon (booming, sinister): gravelly/informative, the deepest.
    put(NpcRace.DEMON, male("Algenib", "Rasalgethi"), female("Gacrux", "Despina"));
    // Wizard (wise, mystical): knowledgeable/informative, weighty.
    put(NpcRace.WIZARD, male("Sadaltager", "Charon"), female("Sulafat", "Vindemiatrix"));
    // Tortugan (warm island folk): friendly/clear and warm/gentle, relaxed mid-depth.
    put(NpcRace.TORTUGAN, male("Achird", "Iapetus"), female("Sulafat", "Vindemiatrix"));

    put(NpcRace.ICYENE, male("Alnilam", "Schedar"), female("Kore", "Despina"));
    // Citizen of Arceuus (ascended, incorporeal): the elf pool's refined, clear timbres,
    // which carry the weightless delivery better than the earthier human voices.
    put(NpcRace.ARCEUUS, male("Iapetus", "Rasalgethi"), female("Vindemiatrix", "Erinome"));
  }

  private static String[] male(String... voices) {
    return voices;
  }

  private static String[] female(String... voices) {
    return voices;
  }

  private void put(NpcRace race, String[] maleVoices, String[] femaleVoices) {
    Map<NpcGender, String[]> byGender = new EnumMap<>(NpcGender.class);
    byGender.put(NpcGender.MALE, maleVoices);
    byGender.put(NpcGender.FEMALE, femaleVoices);
    npcVoices.put(race, byGender);
  }

  /**
   * Resolves the Gemini voice for a spec. The per-NPC seed on the spec spreads same-race/gender
   * NPCs across the sub-pool deterministically; a spec with no seed resolves to the first (anchor)
   * voice of its race/gender pool, so bare specs are stable too.
   */
  String voiceFor(VoiceSpec spec) {
    if (spec == null) {
      return DEFAULT_VOICE;
    }
    NpcGender gender = normalizeGender(spec.gender());
    if (spec.player()) {
      // There is a single player, so it anchors to its configured voice; the per-NPC seed is for
      // NPC variety only and the player carries none.
      return anchor(playerVoices.get(gender));
    }
    if (spec.child()) {
      String[] pool = childVoices.get(gender);
      return (pool == null || pool.length == 0) ? DEFAULT_CHILD_VOICE : pick(pool, spec);
    }
    Map<NpcGender, String[]> byGender = npcVoices.get(spec.race());
    if (byGender == null) {
      return pick(playerVoices.get(gender), spec);
    }
    return pick(byGender.get(gender), spec);
  }

  /** The anchor (index 0) voice of {@code pool}, falling back to the default for an empty pool. */
  private static String anchor(String[] pool) {
    return (pool == null || pool.length == 0) ? DEFAULT_VOICE : pool[0];
  }

  /**
   * Spreads across {@code pool} by the spec's stable per-NPC seed, anchoring bare specs at index 0.
   */
  private static String pick(String[] pool, VoiceSpec spec) {
    if (pool == null || pool.length == 0) {
      return DEFAULT_VOICE;
    }
    if (!spec.hasVoiceSeed()) {
      return pool[0];
    }
    int index = Math.floorMod(Integer.hashCode(spec.voiceSeed()), pool.length);
    return pool[index];
  }

  /** Unknown gender is voiced from the male sub-pool so every spec resolves to a concrete voice. */
  private static NpcGender normalizeGender(NpcGender gender) {
    return gender == NpcGender.FEMALE ? NpcGender.FEMALE : NpcGender.MALE;
  }
}
