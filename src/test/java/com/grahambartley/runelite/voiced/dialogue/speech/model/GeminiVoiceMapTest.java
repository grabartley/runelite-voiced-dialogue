package com.grahambartley.runelite.voiced.dialogue.speech.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** Gender-correctness, determinism, and per-NPC spread of the Gemini race/gender voice map. */
public class GeminiVoiceMapTest {

  private final GeminiVoiceMap map = new GeminiVoiceMap();

  /** The youthful sub-pools a child spec must resolve within, regardless of race. */
  private static final Set<String> CHILD_MALE_POOL = new HashSet<>(java.util.Arrays.asList("Puck"));

  private static final Set<String> CHILD_FEMALE_POOL =
      new HashSet<>(java.util.Arrays.asList("Leda", "Zephyr"));

  /** Races that carry a real race/gender mapping (UNKNOWN intentionally falls back). */
  private static final NpcRace[] MAPPED_RACES = {
    NpcRace.HUMAN,
    NpcRace.ELF,
    NpcRace.DWARF,
    NpcRace.GOBLIN,
    NpcRace.MONKEY,
    NpcRace.GORILLA,
    NpcRace.TROLL,
    NpcRace.UNDEAD,
    NpcRace.DEMON,
    NpcRace.WIZARD,
    NpcRace.TORTUGAN,
    NpcRace.ICYENE,
    NpcRace.ARANEI,
    NpcRace.ARCEUUS
  };

  /**
   * The 30 prebuilt Gemini TTS voices. Every voice the map can emit must be one of these names; a
   * name that is not a real voice fails synthesis at runtime, so the map is checked against the
   * catalog here rather than only by ear.
   */
  private static final Set<String> GEMINI_VOICE_CATALOG =
      new HashSet<>(
          java.util.Arrays.asList(
              "Zephyr",
              "Puck",
              "Charon",
              "Kore",
              "Fenrir",
              "Leda",
              "Orus",
              "Aoede",
              "Callirrhoe",
              "Autonoe",
              "Enceladus",
              "Iapetus",
              "Umbriel",
              "Algieba",
              "Despina",
              "Erinome",
              "Algenib",
              "Rasalgethi",
              "Laomedeia",
              "Achernar",
              "Alnilam",
              "Schedar",
              "Gacrux",
              "Pulcherrima",
              "Achird",
              "Zubenelgenubi",
              "Vindemiatrix",
              "Sadachbia",
              "Sadaltager",
              "Sulafat"));

  @Test
  public void everyRaceGenderPairResolvesToANonBlankVoice() {
    for (NpcRace race : NpcRace.values()) {
      for (NpcGender gender :
          new NpcGender[] {NpcGender.MALE, NpcGender.FEMALE, NpcGender.UNKNOWN}) {
        String voice = map.voiceFor(VoiceSpec.npc(race, gender));
        assertNotNull(race + "/" + gender + " resolves", voice);
        assertFalse(race + "/" + gender + " is non-blank", voice.trim().isEmpty());
      }
    }
  }

  @Test
  public void maleAndFemaleVoicePoolsAreDisjointAcrossEveryRace() {
    Set<String> male = voicesFor(NpcGender.MALE);
    Set<String> female = voicesFor(NpcGender.FEMALE);

    Set<String> overlap = new HashSet<>(male);
    overlap.retainAll(female);
    assertTrue(
        "no voice is shared between genders, so no race voices two genders alike: " + overlap,
        overlap.isEmpty());
  }

  /**
   * Every voice any spec of {@code gender} can produce, sweeping races, a spread of NPC seeds, and
   * both the adult and child life stages, so the gender-disjointness invariant covers children too.
   */
  private Set<String> voicesFor(NpcGender gender) {
    Set<String> voices = new HashSet<>();
    for (NpcRace race : MAPPED_RACES) {
      for (int seed = 0; seed < 64; seed++) {
        voices.add(map.voiceFor(VoiceSpec.npc(race, gender, seed)));
        voices.add(map.voiceFor(VoiceSpec.npc(race, gender, seed, true)));
      }
    }
    voices.add(map.voiceFor(VoiceSpec.player(gender)));
    return voices;
  }

  @Test
  public void sameSpecIsStableAcrossCalls() {
    VoiceSpec spec = VoiceSpec.npc(NpcRace.DWARF, NpcGender.MALE, 4242);
    assertEquals("a given NPC always voices the same way", map.voiceFor(spec), map.voiceFor(spec));
  }

  @Test
  public void sameRaceGenderDifferentNpcsCanGetDifferentVoices() {
    Set<String> seen = new HashSet<>();
    for (int seed = 0; seed < 16; seed++) {
      seen.add(map.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, seed)));
    }
    assertTrue("the per-NPC seed spreads across the sub-pool", seen.size() > 1);
  }

  @Test
  public void unknownRaceFallsBackInsteadOfThrowing() {
    String male = map.voiceFor(VoiceSpec.npc(NpcRace.UNKNOWN, NpcGender.MALE, 7));
    String female = map.voiceFor(VoiceSpec.npc(NpcRace.UNKNOWN, NpcGender.FEMALE, 7));
    assertNotNull(male);
    assertNotNull(female);
  }

  @Test
  public void playerVoiceRespectsGenderAndStaysGenderCorrect() {
    String playerMale = map.voiceFor(VoiceSpec.player(NpcGender.MALE));
    String playerFemale = map.voiceFor(VoiceSpec.player(NpcGender.FEMALE));
    assertFalse("player male and female differ", playerMale.equals(playerFemale));
    assertTrue("player male is in the male pool", voicesFor(NpcGender.MALE).contains(playerMale));
    assertTrue(
        "player female is in the female pool", voicesFor(NpcGender.FEMALE).contains(playerFemale));
  }

  @Test
  public void theNarratorVoiceIsHeldOutOfEveryCharacterPool() {
    String narrator = map.voiceFor(VoiceSpec.NARRATOR);

    assertEquals(GeminiVoiceMap.NARRATOR_VOICE, narrator);
    assertFalse(
        "no character can ever voice as the narrator",
        voicesFor(NpcGender.MALE).contains(narrator));
    assertFalse(
        "no character can ever voice as the narrator",
        voicesFor(NpcGender.FEMALE).contains(narrator));
  }

  @Test
  public void everyEmittableVoiceIsARealGeminiVoice() {
    Set<String> emitted = new HashSet<>();
    emitted.addAll(voicesFor(NpcGender.MALE));
    emitted.addAll(voicesFor(NpcGender.FEMALE));
    emitted.add(GeminiVoiceMap.DEFAULT_VOICE);
    emitted.add(map.voiceFor(VoiceSpec.NARRATOR));
    Set<String> bogus = new HashSet<>(emitted);
    bogus.removeAll(GEMINI_VOICE_CATALOG);
    assertTrue(
        "every mapped voice must be a real Gemini voice, not these: " + bogus, bogus.isEmpty());
  }

  @Test
  public void nullSpecResolvesToTheDefaultVoice() {
    assertEquals(GeminiVoiceMap.DEFAULT_VOICE, map.voiceFor(null));
  }

  @Test
  public void childSpecsOfEveryRaceResolveOnlyWithinTheChildPools() {
    for (NpcRace race : NpcRace.values()) {
      for (int seed = 0; seed < 64; seed++) {
        String male = map.voiceFor(VoiceSpec.npc(race, NpcGender.MALE, seed, true));
        String female = map.voiceFor(VoiceSpec.npc(race, NpcGender.FEMALE, seed, true));
        assertTrue(
            race + " child male resolves in the child-male pool, got " + male,
            CHILD_MALE_POOL.contains(male));
        assertTrue(
            race + " child female resolves in the child-female pool, got " + female,
            CHILD_FEMALE_POOL.contains(female));
      }
    }
  }

  @Test
  public void bareChildSpecsAnchorToPuckAndLeda() {
    assertEquals(
        "Puck",
        map.voiceFor(
            VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, VoiceSpec.UNSPECIFIED_SEED, true)));
    assertEquals(
        "Leda",
        map.voiceFor(
            VoiceSpec.npc(NpcRace.HUMAN, NpcGender.FEMALE, VoiceSpec.UNSPECIFIED_SEED, true)));
  }

  @Test
  public void unknownGenderChildUsesTheChildMaleAnchor() {
    String voice =
        map.voiceFor(
            VoiceSpec.npc(NpcRace.TROLL, NpcGender.UNKNOWN, VoiceSpec.UNSPECIFIED_SEED, true));
    assertEquals("Puck", voice);
  }

  @Test
  public void childPoolsAreGenderDisjointAndDrawnFromTheRealCatalog() {
    Set<String> overlap = new HashSet<>(CHILD_MALE_POOL);
    overlap.retainAll(CHILD_FEMALE_POOL);
    assertTrue("no voice is shared between the child genders: " + overlap, overlap.isEmpty());

    Set<String> all = new HashSet<>(CHILD_MALE_POOL);
    all.addAll(CHILD_FEMALE_POOL);
    all.add(GeminiVoiceMap.DEFAULT_CHILD_VOICE);
    Set<String> bogus = new HashSet<>(all);
    bogus.removeAll(GEMINI_VOICE_CATALOG);
    assertTrue(
        "every child voice must be a real Gemini voice, not these: " + bogus, bogus.isEmpty());
  }

  @Test
  public void sameRaceGenderDifferentChildrenSpreadAcrossTheChildPool() {
    // The male pool is deliberately a single by-ear-approved voice, so the seed spread is
    // observable on the female pool.
    Set<String> seen = new HashSet<>();
    for (int seed = 0; seed < 16; seed++) {
      seen.add(map.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.FEMALE, seed, true)));
    }
    assertTrue("the per-NPC seed spreads children across the child sub-pool", seen.size() > 1);
  }

  @Test
  public void adultSpecsKeepTheirAdultRaceAnchors() {
    // The child pools reuse voices the map already trusts for goblins and monkeys, so adults of
    // OTHER races must be unaffected: a human male still anchors to Charon, not Puck.
    assertEquals(
        GeminiVoiceMap.DEFAULT_VOICE, map.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE)));
    assertEquals("Despina", map.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.FEMALE)));
  }

  @Test
  public void arceuusSharesTheElfPoolsAndIntroducesNoNewVoice() {
    Set<String> elfMale = new HashSet<>();
    Set<String> elfFemale = new HashSet<>();
    Set<String> arceuusMale = new HashSet<>();
    Set<String> arceuusFemale = new HashSet<>();
    for (int seed = 0; seed < 64; seed++) {
      elfMale.add(map.voiceFor(VoiceSpec.npc(NpcRace.ELF, NpcGender.MALE, seed)));
      elfFemale.add(map.voiceFor(VoiceSpec.npc(NpcRace.ELF, NpcGender.FEMALE, seed)));
      arceuusMale.add(map.voiceFor(VoiceSpec.npc(NpcRace.ARCEUUS, NpcGender.MALE, seed)));
      arceuusFemale.add(map.voiceFor(VoiceSpec.npc(NpcRace.ARCEUUS, NpcGender.FEMALE, seed)));
    }
    assertEquals("Arceuus males draw the elf male pool", elfMale, arceuusMale);
    assertEquals("Arceuus females draw the elf female pool", elfFemale, arceuusFemale);
  }

  @Test
  public void arceuusSpreadsAcrossItsPoolAndStaysGenderCorrect() {
    Set<String> male = new HashSet<>();
    Set<String> female = new HashSet<>();
    for (int seed = 0; seed < 16; seed++) {
      male.add(map.voiceFor(VoiceSpec.npc(NpcRace.ARCEUUS, NpcGender.MALE, seed)));
      female.add(map.voiceFor(VoiceSpec.npc(NpcRace.ARCEUUS, NpcGender.FEMALE, seed)));
    }
    assertTrue("two Arceuus males of the same gender can differ", male.size() > 1);
    assertTrue("two Arceuus females of the same gender can differ", female.size() > 1);
    Set<String> overlap = new HashSet<>(male);
    overlap.retainAll(female);
    assertTrue("no Arceuus voice serves both genders: " + overlap, overlap.isEmpty());
  }
}
