package com.grahambartley.runelite.voiced.dialogue.speech.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class GeminiVoiceMapTest {

  private final GeminiVoiceMap map = new GeminiVoiceMap();

  private static final GeminiVoiceMap TWO_REGIONS =
      new GeminiVoiceMap(
          new GeminiVoiceRegions(
              new JsonParser()
                  .parse(
                      "{\"IRISH\":{\"playerKeywords\":[\"irish\"],\"MALE\":[\"ie-m-1\",\"ie-m-2\"],"
                          + "\"FEMALE\":[\"ie-f-1\"],\"CHILD_MALE\":[\"ie-young\"]},"
                          + "\"SOUTHERN_ENGLISH\":{\"MALE\":[\"se-m-1\",\"se-m-2\"],"
                          + "\"FEMALE\":[\"se-f-1\"]}}")
                  .getAsJsonObject()));

  private static final String[] REGION_KEYS = {
    "SOUTHERN_ENGLISH",
    "WEST_COUNTRY",
    "SCOUSE",
    "MANCUNIAN",
    "GEORDIE",
    "SCOTTISH",
    "IRISH",
    "AUSTRALIAN",
    "NEW_ZEALAND",
    "ITALIAN",
    "EGYPTIAN_ARABIC",
    "POLISH",
    "JAPANESE",
    "INDIAN_ENGLISH"
  };

  private static final Set<String> CHILD_MALE_POOL = new HashSet<>(Arrays.asList("Puck"));

  private static final Set<String> CHILD_FEMALE_POOL =
      new HashSet<>(Arrays.asList("Leda", "Zephyr"));

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
          Arrays.asList(
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
    voices.add(map.voiceFor(VoiceSpec.follower(gender), null));
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
  public void followerVoiceRespectsGenderAndIsStableAcrossCalls() {
    String followerMale = map.voiceFor(VoiceSpec.follower(NpcGender.MALE), null);
    String followerFemale = map.voiceFor(VoiceSpec.follower(NpcGender.FEMALE), null);

    assertNotNull(followerMale);
    assertNotNull(followerFemale);
    assertFalse("follower male and female differ", followerMale.equals(followerFemale));
    assertEquals(followerMale, map.voiceFor(VoiceSpec.follower(NpcGender.MALE), null));
  }

  @Test
  public void theFollowerNeverBorrowsThePlayersVoice() {
    for (NpcGender gender : new NpcGender[] {NpcGender.MALE, NpcGender.FEMALE}) {
      assertFalse(
          "the follower would otherwise sound exactly like its owner",
          map.voiceFor(VoiceSpec.follower(gender), null)
              .equals(map.voiceFor(VoiceSpec.player(gender), null)));
    }
  }

  @Test
  public void theFollowerIsNeverTheNarrator() {
    assertFalse(
        map.voiceFor(VoiceSpec.follower(NpcGender.MALE), null)
            .equals(map.voiceFor(VoiceSpec.NARRATOR, null)));
    assertFalse(
        map.voiceFor(VoiceSpec.follower(NpcGender.FEMALE), null)
            .equals(map.voiceFor(VoiceSpec.NARRATOR, null)));
  }

  @Test
  public void anUnknownFollowerGenderStillResolvesToARealVoice() {
    assertTrue(
        GEMINI_VOICE_CATALOG.contains(map.voiceFor(VoiceSpec.follower(NpcGender.UNKNOWN), null)));
  }

  @Test
  public void theNarratorVoiceIsHeldOutOfEveryCharacterPool() {
    String narrator = map.voiceFor(VoiceSpec.NARRATOR, null);

    assertEquals(GeminiVoiceRegions.bundled().narratorVoice(), narrator);
    GeminiVoiceRegions bundled = GeminiVoiceRegions.bundled();
    for (String region : REGION_KEYS) {
      for (int seed = 0; seed < 400; seed++) {
        for (NpcGender gender : new NpcGender[] {NpcGender.MALE, NpcGender.FEMALE}) {
          assertFalse(narrator.equals(bundled.voiceFor(region, gender, seed)));
          assertFalse(narrator.equals(bundled.childVoiceFor(region, gender, seed)));
        }
      }
    }
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
    emitted.add(GeminiVoiceMap.FALLBACK_NARRATOR_VOICE);
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
              new JsonParser()
                  .parse(
                      "{\"IRISH\":{\"playerKeywords\":[\"irish\"],\"MALE\":[\"ie-m-1\",\"ie-m-2\"],"
                          + "\"FEMALE\":[]}}")
                  .getAsJsonObject()));

  private static CharacterProfile inRegion(String region) {
    return new CharacterProfile("Npc", "Strong accent", null, "Plain.", "Steady.", null, region);
  }

  private static CharacterProfile overridden(String accent, String bundledRegion) {
    return new CharacterProfile(
        "Npc", accent, null, "Plain.", "Steady.", null, bundledRegion, true);
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
  public void aChildWithARegionTakesThatRegionsYoungestVoices() {
    GeminiVoiceMap withChildren =
        new GeminiVoiceMap(
            new GeminiVoiceRegions(
                new JsonParser()
                    .parse(
                        "{\"IRISH\":{\"MALE\":[\"ie-m-1\",\"ie-m-2\"],\"CHILD_MALE\":[\"ie-young\"]}}")
                    .getAsJsonObject()));
    assertEquals(
        "ie-young",
        withChildren.voiceFor(
            VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 5, true), inRegion("IRISH")));
  }

  @Test
  public void aChildWhoseRegionHasNoYoungVoicesKeepsTheChildPool() {
    String voice =
        REGIONAL.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 5, true), inRegion("IRISH"));
    assertTrue(CHILD_MALE_POOL.contains(voice));
  }

  @Test
  public void theNarratorIgnoresRegions() {
    assertEquals(
        GeminiVoiceMap.FALLBACK_NARRATOR_VOICE,
        REGIONAL.voiceFor(VoiceSpec.NARRATOR, inRegion("IRISH")));
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
  public void theDefaultPlayerAccentTakesThePinnedSouthernEnglishVoices() {
    CharacterProfile southern =
        new CharacterProfile(
            "Adventurer",
            "Strong friendly, down-to-earth southern English accent, British English pronunciation",
            "Plain.",
            "Steady.");
    GeminiVoiceRegions bundled = GeminiVoiceRegions.bundled();
    int seed = GeminiVoiceMap.PLAYER_SEED;
    assertNotEquals(
        "en-gb-podcaster-1", bundled.voiceFor("SOUTHERN_ENGLISH", NpcGender.MALE, seed));
    assertNotEquals(
        "en-gb-commercial-6", bundled.voiceFor("SOUTHERN_ENGLISH", NpcGender.FEMALE, seed));
    assertEquals("en-gb-podcaster-1", map.voiceFor(VoiceSpec.player(NpcGender.MALE), southern));
    assertEquals("en-gb-commercial-6", map.voiceFor(VoiceSpec.player(NpcGender.FEMALE), southern));
  }

  @Test
  public void aPlayerAccentNamingNoRegionKeepsThePlayerVoice() {
    CharacterProfile welsh =
        new CharacterProfile("Adventurer", "Strong Welsh accent", "Plain.", "Steady.");
    assertEquals(
        map.voiceFor(VoiceSpec.player(NpcGender.MALE), null),
        REGIONAL.voiceFor(VoiceSpec.player(NpcGender.MALE), welsh));
  }

  private static CharacterProfile accented(String accent) {
    return new CharacterProfile("Companion", accent, "Plain.", "Steady.");
  }

  @Test
  public void theFollowerIsVoicedFromTheRegionItsTypedAccentNames() {
    String voice = REGIONAL.voiceFor(VoiceSpec.follower(NpcGender.MALE), accented("Irish"));
    assertTrue(voice, voice.startsWith("ie-m-"));
  }

  @Test
  public void aFollowerSharingItsOwnersRegionStillSoundsDifferent() {
    CharacterProfile irish = accented("Strong Dublin Irish accent");
    assertNotEquals(
        REGIONAL.voiceFor(VoiceSpec.player(NpcGender.MALE), irish),
        REGIONAL.voiceFor(VoiceSpec.follower(NpcGender.MALE), irish));
  }

  @Test
  public void aFollowerAccentNamingNoRegionKeepsTheFollowerVoice() {
    assertEquals(
        map.voiceFor(VoiceSpec.follower(NpcGender.MALE), null),
        REGIONAL.voiceFor(VoiceSpec.follower(NpcGender.MALE), accented("Strong Welsh accent")));
  }

  @Test
  public void aFollowerWhoseRegionHoldsOnlyItsOwnersVoiceKeepsTheFollowerVoice() {
    GeminiVoiceMap single =
        new GeminiVoiceMap(
            new GeminiVoiceRegions(
                new JsonParser()
                    .parse("{\"IRISH\":{\"playerKeywords\":[\"irish\"],\"MALE\":[\"ie-m-1\"]}}")
                    .getAsJsonObject()));
    assertEquals(
        map.voiceFor(VoiceSpec.follower(NpcGender.MALE), null),
        single.voiceFor(VoiceSpec.follower(NpcGender.MALE), accented("Irish")));
  }

  @Test
  public void inEveryBundledRegionTheFollowerIsNeitherItsOwnerNorTheNarrator() {
    String narrator = map.voiceFor(VoiceSpec.NARRATOR, null);
    for (String accent : BUNDLED_REGION_ACCENTS) {
      for (NpcGender gender : new NpcGender[] {NpcGender.MALE, NpcGender.FEMALE}) {
        String follower = map.voiceFor(VoiceSpec.follower(gender), accented(accent));
        assertNotEquals(accent, map.voiceFor(VoiceSpec.follower(gender), null), follower);
        assertNotEquals(accent, map.voiceFor(VoiceSpec.player(gender), accented(accent)), follower);
        assertNotEquals(accent, narrator, follower);
      }
    }
  }

  private static final String[] BUNDLED_REGION_ACCENTS = {
    "West Country",
    "Scouse",
    "Mancunian",
    "Geordie",
    "Scottish",
    "Irish",
    "Australian",
    "New Zealand",
    "Italian",
    "Egyptian",
    "Polish",
    "Japanese",
    "Norse",
    "Southern English",
    "Indian"
  };

  @Test
  public void anNpcAccentOverrideNamingARegionIsVoicedFromThatRegion() {
    VoiceSpec spec = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 42);
    CharacterProfile irish =
        overridden("Strong Irish accent, Irish English pronunciation", "SOUTHERN_ENGLISH");
    String voice = TWO_REGIONS.voiceFor(spec, irish);
    assertTrue(voice, voice.startsWith("ie-m-"));
    assertEquals("IRISH", TWO_REGIONS.regionFor(spec, irish));
  }

  @Test
  public void anOverrideRegionIsSeededLikeABundledNpcInThatRegion() {
    VoiceSpec spec = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 1234);
    assertEquals(
        TWO_REGIONS.voiceFor(spec, inRegion("IRISH")),
        TWO_REGIONS.voiceFor(spec, overridden("Irish", "SOUTHERN_ENGLISH")));
  }

  @Test
  public void anOverrideRegionKeepsTheGender() {
    assertEquals(
        "ie-f-1",
        TWO_REGIONS.voiceFor(
            VoiceSpec.npc(NpcRace.HUMAN, NpcGender.FEMALE, 7),
            overridden("Irish", "SOUTHERN_ENGLISH")));
  }

  @Test
  public void anOverrideRegionAppliesToTheChildPool() {
    assertEquals(
        "ie-young",
        TWO_REGIONS.voiceFor(
            VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 7, true),
            overridden("Irish", "SOUTHERN_ENGLISH")));
  }

  @Test
  public void anOverrideAccentNamingNoRegionKeepsTheBundledRegion() {
    VoiceSpec spec = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 42);
    CharacterProfile welsh = overridden("Strong Welsh accent", "SOUTHERN_ENGLISH");
    assertEquals(
        TWO_REGIONS.voiceFor(spec, inRegion("SOUTHERN_ENGLISH")),
        TWO_REGIONS.voiceFor(spec, welsh));
    assertEquals("SOUTHERN_ENGLISH", TWO_REGIONS.regionFor(spec, welsh));
  }

  @Test
  public void aBundledAccentNamingARegionDoesNotMoveTheVoice() {
    VoiceSpec spec = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 42);
    CharacterProfile bundled =
        new CharacterProfile(
            "Npc", "Irish accent", null, "Plain.", "Steady.", null, "SOUTHERN_ENGLISH");
    assertTrue(TWO_REGIONS.voiceFor(spec, bundled).startsWith("se-m-"));
    assertEquals("SOUTHERN_ENGLISH", TWO_REGIONS.regionFor(spec, bundled));
  }

  @Test
  public void anUnseededNpcIgnoresTheOverrideRegion() {
    VoiceSpec unseeded = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE);
    CharacterProfile irish = overridden("Irish", "SOUTHERN_ENGLISH");
    assertEquals(map.voiceFor(unseeded, null), TWO_REGIONS.voiceFor(unseeded, irish));
    assertNull(TWO_REGIONS.regionFor(unseeded, irish));
  }

  @Test
  public void regionForMatchesThePlayersTypedAccentAndSkipsTheNarrator() {
    CharacterProfile irish = new CharacterProfile("Adventurer", "Irish", "Plain.", "Steady.");
    assertEquals("IRISH", TWO_REGIONS.regionFor(VoiceSpec.player(NpcGender.MALE), irish));
    assertNull(TWO_REGIONS.regionFor(VoiceSpec.NARRATOR, irish));
    assertNull(TWO_REGIONS.regionFor(null, irish));
  }

  @Test
  public void regionForMatchesTheFollowersTypedAccent() {
    assertEquals(
        "IRISH", TWO_REGIONS.regionFor(VoiceSpec.follower(NpcGender.MALE), accented("Irish")));
  }

  @Test
  public void aPlayerTypingAnIndianAccentIsVoicedByANativeIndianEnglishVoice() {
    CharacterProfile indian =
        new CharacterProfile(
            "Adventurer",
            "Strong Indian accent, Indian English pronunciation",
            "Plain.",
            "Steady.");
    for (NpcGender gender : new NpcGender[] {NpcGender.MALE, NpcGender.FEMALE}) {
      String voice = map.voiceFor(VoiceSpec.player(gender), indian);
      assertTrue(voice, voice.startsWith("en-in-"));
      assertEquals("INDIAN_ENGLISH", map.regionFor(VoiceSpec.player(gender), indian));
    }
  }

  @Test
  public void anNpcAccentOverrideNamingIndiaIsVoicedByANativeIndianEnglishVoice() {
    CharacterProfile indian =
        overridden("Strong Indian accent, Indian English pronunciation", "SOUTHERN_ENGLISH");
    for (NpcGender gender : new NpcGender[] {NpcGender.MALE, NpcGender.FEMALE}) {
      for (boolean child : new boolean[] {false, true}) {
        VoiceSpec spec = VoiceSpec.npc(NpcRace.HUMAN, gender, 42, child);
        String voice = map.voiceFor(spec, indian);
        assertTrue(voice, voice.startsWith("en-in-"));
        assertEquals("INDIAN_ENGLISH", map.regionFor(spec, indian));
      }
    }
  }

  private static Set<String> pool(String... voices) {
    return new HashSet<>(Arrays.asList(voices));
  }

  @Test
  public void anAgedNpcIsVoicedByTheRegionVoicesClosestToItsAge() {
    java.util.Map<String, Integer> ages = new java.util.HashMap<>();
    ages.put("se-young-1", 25);
    ages.put("se-young-2", 27);
    ages.put("se-young-3", 29);
    ages.put("se-old", 62);
    GeminiVoiceMap aged =
        new GeminiVoiceMap(
            new GeminiVoiceRegions(
                new JsonParser()
                    .parse(
                        "{\"SE\":{\"MALE\":[\"se-young-1\",\"se-young-2\",\"se-young-3\",\"se-old\"]}}")
                    .getAsJsonObject(),
                null,
                ages));
    CharacterProfile old =
        new CharacterProfile(
            "Npc", "Strong accent", null, "Gruff.", "Slow.", null, "SE", false, 70);
    for (int seed = 0; seed < 50; seed++) {
      String voice = aged.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, seed), old);
      assertTrue(voice, !voice.equals("se-young-1"));
    }
  }

  @Test
  public void aChildIgnoresItsAgeAndKeepsTheChildPool() {
    CharacterProfile agedChild =
        new CharacterProfile(
            "Npc", "Strong accent", null, "Bright.", "Quick.", null, "IRISH", false, 70);
    assertEquals(
        "ie-young",
        TWO_REGIONS.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 5, true), agedChild));
  }

  @Test
  public void anOldSouthernEnglishManIsVoicedByTheOldestSouthernEnglishMen() {
    Set<String> oldest =
        new HashSet<>(Arrays.asList("en-gb-training-1", "en-gb-tutor-8", "en-gb-tutor-9"));
    CharacterProfile old =
        new CharacterProfile(
            "Dr Harlow",
            "Strong accent",
            null,
            "Gruff.",
            "Slow.",
            null,
            "SOUTHERN_ENGLISH",
            false,
            65);
    for (int seed = 0; seed < 100; seed++) {
      String voice = map.voiceFor(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, seed), old);
      assertTrue(voice, oldest.contains(voice));
    }
  }
}
