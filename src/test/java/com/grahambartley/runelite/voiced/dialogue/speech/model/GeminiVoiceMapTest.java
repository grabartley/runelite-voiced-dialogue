package com.grahambartley.runelite.voiced.dialogue.speech.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class GeminiVoiceMapTest {

  private final GeminiVoiceMap map = new GeminiVoiceMap();

  private static final Set<String> CHILD_MALE_POOL = new HashSet<>(java.util.Arrays.asList("Puck"));

  private static final Set<String> CHILD_FEMALE_POOL =
      new HashSet<>(java.util.Arrays.asList("Leda", "Zephyr"));

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
    NpcRace.ARCEUUS,
    NpcRace.ARANEI,
    NpcRace.DOG,
    NpcRace.CRAB,
    NpcRace.PENGUIN
  };

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
        String voice = map.voiceFor(VoiceSpec.npc(race, gender), null);
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

  private Set<String> voicesFor(NpcGender gender) {
    Set<String> voices = new HashSet<>();
    for (NpcRace race : MAPPED_RACES) {
      for (int seed = 0; seed < 64; seed++) {
        voices.add(map.voiceFor(VoiceSpec.npc(race, gender, seed), null));
        voices.add(map.voiceFor(VoiceSpec.npc(race, gender, seed, true), null));
      }
    }
    voices.add(map.voiceFor(VoiceSpec.player(gender), null));
    return voices;
  }

  @Test
  public void sameSpecIsStableAcrossCalls() {
    VoiceSpec spec = VoiceSpec.npc(NpcRace.DWARF, NpcGender.MALE, 4242);
    assertEquals(
        "a given NPC always voices the same way",
        map.voiceFor(spec, null),
        map.voiceFor(spec, null));
  }

  @Test
  public void sameRaceGenderDifferentNpcsCanGetDifferentVoices() {
    Set<String> seen = new HashSet<>();
    for (int seed = 0; seed < 16; seed++) {
      seen.add(map.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, seed), null));
    }
    assertTrue("the per-NPC seed spreads across the sub-pool", seen.size() > 1);
  }

  @Test
  public void unknownRaceFallsBackInsteadOfThrowing() {
    String male = map.voiceFor(VoiceSpec.npc(NpcRace.UNKNOWN, NpcGender.MALE, 7), null);
    String female = map.voiceFor(VoiceSpec.npc(NpcRace.UNKNOWN, NpcGender.FEMALE, 7), null);
    assertNotNull(male);
    assertNotNull(female);
  }

  @Test
  public void playerVoiceRespectsGenderAndStaysGenderCorrect() {
    String playerMale = map.voiceFor(VoiceSpec.player(NpcGender.MALE), null);
    String playerFemale = map.voiceFor(VoiceSpec.player(NpcGender.FEMALE), null);
    assertFalse("player male and female differ", playerMale.equals(playerFemale));
    assertTrue("player male is in the male pool", voicesFor(NpcGender.MALE).contains(playerMale));
    assertTrue(
        "player female is in the female pool", voicesFor(NpcGender.FEMALE).contains(playerFemale));
  }

  @Test
  public void theNarratorVoiceIsHeldOutOfEveryCharacterPool() {
    String narrator = map.voiceFor(VoiceSpec.NARRATOR, null);

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
    emitted.add(map.voiceFor(VoiceSpec.NARRATOR, null));
    Set<String> bogus = new HashSet<>(emitted);
    bogus.removeAll(GEMINI_VOICE_CATALOG);
    assertTrue(
        "every mapped voice must be a real Gemini voice, not these: " + bogus, bogus.isEmpty());
  }

  @Test
  public void nullSpecResolvesToTheDefaultVoice() {
    assertEquals(GeminiVoiceMap.DEFAULT_VOICE, map.voiceFor(null, null));
  }

  @Test
  public void childSpecsOfEveryRaceResolveOnlyWithinTheChildPools() {
    for (NpcRace race : NpcRace.values()) {
      for (int seed = 0; seed < 64; seed++) {
        String male = map.voiceFor(VoiceSpec.npc(race, NpcGender.MALE, seed, true), null);
        String female = map.voiceFor(VoiceSpec.npc(race, NpcGender.FEMALE, seed, true), null);
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
            VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, VoiceSpec.UNSPECIFIED_SEED, true), null));
    assertEquals(
        "Leda",
        map.voiceFor(
            VoiceSpec.npc(NpcRace.HUMAN, NpcGender.FEMALE, VoiceSpec.UNSPECIFIED_SEED, true),
            null));
  }

  @Test
  public void unknownGenderChildUsesTheChildMaleAnchor() {
    String voice =
        map.voiceFor(
            VoiceSpec.npc(NpcRace.TROLL, NpcGender.UNKNOWN, VoiceSpec.UNSPECIFIED_SEED, true),
            null);
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
    Set<String> seen = new HashSet<>();
    for (int seed = 0; seed < 16; seed++) {
      seen.add(map.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.FEMALE, seed, true), null));
    }
    assertTrue("the per-NPC seed spreads children across the child sub-pool", seen.size() > 1);
  }

  @Test
  public void adultSpecsKeepTheirAdultRaceAnchors() {
    assertEquals(
        GeminiVoiceMap.DEFAULT_VOICE,
        map.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), null));
    assertEquals("Despina", map.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.FEMALE), null));
  }

  @Test
  public void arceuusSharesTheElfPoolsAndIntroducesNoNewVoice() {
    Set<String> elfMale = new HashSet<>();
    Set<String> elfFemale = new HashSet<>();
    Set<String> arceuusMale = new HashSet<>();
    Set<String> arceuusFemale = new HashSet<>();
    for (int seed = 0; seed < 64; seed++) {
      elfMale.add(map.voiceFor(VoiceSpec.npc(NpcRace.ELF, NpcGender.MALE, seed), null));
      elfFemale.add(map.voiceFor(VoiceSpec.npc(NpcRace.ELF, NpcGender.FEMALE, seed), null));
      arceuusMale.add(map.voiceFor(VoiceSpec.npc(NpcRace.ARCEUUS, NpcGender.MALE, seed), null));
      arceuusFemale.add(map.voiceFor(VoiceSpec.npc(NpcRace.ARCEUUS, NpcGender.FEMALE, seed), null));
    }
    assertEquals("Arceuus males draw the elf male pool", elfMale, arceuusMale);
    assertEquals("Arceuus females draw the elf female pool", elfFemale, arceuusFemale);
  }

  @Test
  public void arceuusSpreadsAcrossItsPoolAndStaysGenderCorrect() {
    Set<String> male = new HashSet<>();
    Set<String> female = new HashSet<>();
    for (int seed = 0; seed < 16; seed++) {
      male.add(map.voiceFor(VoiceSpec.npc(NpcRace.ARCEUUS, NpcGender.MALE, seed), null));
      female.add(map.voiceFor(VoiceSpec.npc(NpcRace.ARCEUUS, NpcGender.FEMALE, seed), null));
    }
    assertTrue("two Arceuus males of the same gender can differ", male.size() > 1);
    assertTrue("two Arceuus females of the same gender can differ", female.size() > 1);
    Set<String> overlap = new HashSet<>(male);
    overlap.retainAll(female);
    assertTrue("no Arceuus voice serves both genders: " + overlap, overlap.isEmpty());
  }

  @Test
  public void araneiPairTheUndeadBreathyAnchorWithTheHumanClearOne() {
    assertPools(NpcRace.ARANEI, pool("Enceladus", "Iapetus"), pool("Achernar", "Erinome"));
  }

  @Test
  public void dogsPairTheExcitableAnchorWithTheDeepestOne() {
    assertPools(NpcRace.DOG, pool("Fenrir", "Orus"), pool("Pulcherrima", "Gacrux"));
  }

  @Test
  public void crabsDrawTheBrightGoblinAndMonkeyTimbres() {
    assertPools(NpcRace.CRAB, pool("Zubenelgenubi", "Sadachbia"), pool("Pulcherrima", "Laomedeia"));
  }

  @Test
  public void penguinsDrawTheUpbeatAndCasualBrightTimbres() {
    assertPools(NpcRace.PENGUIN, pool("Puck", "Zubenelgenubi"), pool("Zephyr", "Laomedeia"));
  }

  private void assertPools(NpcRace race, Set<String> expectedMale, Set<String> expectedFemale) {
    Set<String> male = new HashSet<>();
    Set<String> female = new HashSet<>();
    for (int seed = 0; seed < 64; seed++) {
      male.add(map.voiceFor(VoiceSpec.npc(race, NpcGender.MALE, seed), null));
      female.add(map.voiceFor(VoiceSpec.npc(race, NpcGender.FEMALE, seed), null));
    }
    assertEquals(race + " males draw their stated pool", expectedMale, male);
    assertEquals(race + " females draw their stated pool", expectedFemale, female);
    assertTrue(race + " draws only catalog voices", GEMINI_VOICE_CATALOG.containsAll(male));
    assertTrue(race + " draws only catalog voices", GEMINI_VOICE_CATALOG.containsAll(female));
    Set<String> overlap = new HashSet<>(male);
    overlap.retainAll(female);
    assertTrue("no " + race + " voice serves both genders: " + overlap, overlap.isEmpty());
  }

  private static final GeminiVoiceMap REGIONAL =
      new GeminiVoiceMap(
          new GeminiVoiceRegions(
              new com.google.gson.JsonParser()
                  .parse(
                      "{\"IRISH\":{\"playerKeywords\":[\"irish\"],\"MALE\":[\"ie-m-1\",\"ie-m-2\"],"
                          + "\"FEMALE\":[]}}")
                  .getAsJsonObject()));

  private static CharacterProfile inRegion(String region) {
    return new CharacterProfile("Npc", "Strong accent", "Plain.", "Steady.", null, region);
  }

  @Test
  public void anNpcWithARegionIsVoicedFromThatRegionsPool() {
    String voice =
        REGIONAL.voiceFor(VoiceSpec.npc(NpcRace.TROLL, NpcGender.MALE, 99), inRegion("IRISH"));
    assertTrue(voice, voice.startsWith("ie-m-"));
  }

  @Test
  public void aRegionalNpcKeepsOneVoiceOnEveryLine() {
    VoiceSpec spec = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 1234);
    String first = REGIONAL.voiceFor(spec, inRegion("IRISH"));
    for (int i = 0; i < 10; i++) {
      assertEquals(first, REGIONAL.voiceFor(spec, inRegion("IRISH")));
    }
  }

  @Test
  public void aRegionWithNoVoicesForTheGenderFallsBackToTheRacePool() {
    assertEquals(
        map.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.FEMALE, 5), null),
        REGIONAL.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.FEMALE, 5), inRegion("IRISH")));
  }

  @Test
  public void aChildKeepsTheChildPoolWhateverItsRegion() {
    String voice =
        REGIONAL.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 5, true), inRegion("IRISH"));
    assertTrue(CHILD_MALE_POOL.contains(voice));
  }

  @Test
  public void theNarratorIgnoresRegions() {
    assertEquals(
        GeminiVoiceMap.NARRATOR_VOICE, REGIONAL.voiceFor(VoiceSpec.NARRATOR, inRegion("IRISH")));
  }

  @Test
  public void thePlayerIsVoicedFromTheRegionTheirTypedAccentNames() {
    CharacterProfile irish =
        new CharacterProfile("Adventurer", "Strong Dublin Irish accent", "Plain.", "Steady.");
    String voice = REGIONAL.voiceFor(VoiceSpec.player(NpcGender.MALE), irish);
    assertTrue(voice, voice.startsWith("ie-m-"));
    assertEquals(voice, REGIONAL.voiceFor(VoiceSpec.player(NpcGender.MALE), irish));
  }

  @Test
  public void aPlayerAccentNamingNoRegionKeepsThePlayerVoice() {
    CharacterProfile welsh =
        new CharacterProfile("Adventurer", "Strong Welsh accent", "Plain.", "Steady.");
    assertEquals(
        map.voiceFor(VoiceSpec.player(NpcGender.MALE), null),
        REGIONAL.voiceFor(VoiceSpec.player(NpcGender.MALE), welsh));
  }

  private static Set<String> pool(String... voices) {
    return new HashSet<>(java.util.Arrays.asList(voices));
  }
}
