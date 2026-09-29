package com.grahambartley.runelite.voiced.dialogue.speech.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.mockito.Answers;

public class GeminiVoiceRegionsTest {

  private static final String JSON =
      "{\"SCOTTISH\":{\"playerKeywords\":[\"scottish\",\"glasgow\"],"
          + "\"MALE\":[\"gb-m-1\",\"gb-m-2\",\"gb-m-3\",\"gb-m-4\"],\"FEMALE\":[]},"
          + "\"IRISH\":{\"playerKeywords\":[\"irish\",\"dublin\"],"
          + "\"MALE\":[\"ie-m-1\",\"ie-m-2\"],\"FEMALE\":[\"ie-f-1\"]},"
          + "\"SOUTHERN_ENGLISH\":{\"playerKeywords\":[\"southern\",\"received pronunciation\"],"
          + "\"MALE\":[\"en-m-1\"],\"FEMALE\":[\"en-f-1\"]}}";

  private static GeminiVoiceRegions regions(String json) {
    return new GeminiVoiceRegions(new JsonParser().parse(json).getAsJsonObject());
  }

  private static final GeminiVoiceRegions REGIONS = regions(JSON);

  private static final GeminiVoiceRegions PINNED_IRISH =
      regions(
          JSON.replace(
              "\"FEMALE\":[\"ie-f-1\"]", "\"FEMALE\":[\"ie-f-1\"],\"PLAYER_MALE\":\"ie-m-2\""));

  @Test
  public void aPinnedPlayerVoiceWinsOverTheSeededPick() {
    assertNotEquals("ie-m-2", REGIONS.voiceFor("IRISH", NpcGender.MALE, 0));
    assertEquals("ie-m-2", PINNED_IRISH.playerVoiceFor("IRISH", NpcGender.MALE, 0));
    assertEquals("ie-f-1", PINNED_IRISH.playerVoiceFor("IRISH", NpcGender.FEMALE, 0));
  }

  @Test
  public void anUnpinnedPlayerTakesTheSeededPick() {
    assertEquals(
        REGIONS.voiceFor("SCOTTISH", NpcGender.MALE, 0),
        REGIONS.playerVoiceFor("SCOTTISH", NpcGender.MALE, 0));
    assertNull(REGIONS.playerVoiceFor(null, NpcGender.MALE, 0));
  }

  @Test
  public void aPinnedPlayerVoiceLeavesNpcPicksAlone() {
    for (int seed = 0; seed < 50; seed++) {
      assertEquals(
          REGIONS.voiceFor("IRISH", NpcGender.MALE, seed),
          PINNED_IRISH.voiceFor("IRISH", NpcGender.MALE, seed));
    }
  }

  @Test
  public void anNpcAlwaysGetsTheSameVoice() {
    String first = REGIONS.voiceFor("SCOTTISH", NpcGender.MALE, 4242);
    for (int i = 0; i < 20; i++) {
      assertEquals(first, REGIONS.voiceFor("SCOTTISH", NpcGender.MALE, 4242));
      assertEquals(first, regions(JSON).voiceFor("SCOTTISH", NpcGender.MALE, 4242));
    }
  }

  @Test
  public void theVoiceComesFromThePoolForTheGender() {
    assertTrue(REGIONS.voiceFor("IRISH", NpcGender.MALE, 7).startsWith("ie-m-"));
    assertEquals("ie-f-1", REGIONS.voiceFor("IRISH", NpcGender.FEMALE, 7));
  }

  @Test
  public void anExcludedVoiceIsNeverPicked() {
    for (int seed = 0; seed < 200; seed++) {
      String owners = REGIONS.voiceFor("SCOTTISH", NpcGender.MALE, seed);
      assertFalse(owners.equals(REGIONS.voiceExcluding("SCOTTISH", NpcGender.MALE, seed, owners)));
    }
  }

  @Test
  public void excludingAVoiceOutsideThePoolPicksAsUsual() {
    assertEquals(
        REGIONS.voiceFor("SCOTTISH", NpcGender.MALE, 9),
        REGIONS.voiceExcluding("SCOTTISH", NpcGender.MALE, 9, "ie-m-1"));
  }

  @Test
  public void excludingThePoolsOnlyVoiceLeavesNoVoice() {
    assertNull(REGIONS.voiceExcluding("SOUTHERN_ENGLISH", NpcGender.MALE, 3, "en-m-1"));
  }

  @Test
  public void differentNpcsSpreadAcrossThePool() {
    Set<String> voices = new HashSet<>();
    for (int seed = 0; seed < 200; seed++) {
      voices.add(REGIONS.voiceFor("SCOTTISH", NpcGender.MALE, seed));
    }
    assertEquals(4, voices.size());
  }

  @Test
  public void addingAVoiceOnlyMovesTheNpcsThatNowPickIt() {
    GeminiVoiceRegions grown = regions(JSON.replace("\"gb-m-4\"]", "\"gb-m-4\",\"gb-m-5\"]"));
    for (int seed = 0; seed < 500; seed++) {
      String before = REGIONS.voiceFor("SCOTTISH", NpcGender.MALE, seed);
      String after = grown.voiceFor("SCOTTISH", NpcGender.MALE, seed);
      assertTrue(after.equals(before) || after.equals("gb-m-5"));
    }
  }

  @Test
  public void anEmptyPoolForTheGenderHasNoVoice() {
    assertNull(REGIONS.voiceFor("SCOTTISH", NpcGender.FEMALE, 1));
  }

  @Test
  public void anUnknownOrMissingRegionHasNoVoice() {
    assertNull(REGIONS.voiceFor("WELSH", NpcGender.MALE, 1));
    assertNull(REGIONS.voiceFor(null, NpcGender.MALE, 1));
  }

  @Test
  public void theTypedAccentPicksTheFirstRegionWhoseKeywordItNames() {
    assertEquals(
        "IRISH",
        REGIONS.regionForAccent("Strong Dublin Irish accent, Irish English pronunciation"));
    assertEquals("SCOTTISH", REGIONS.regionForAccent("Glasgow, as heard in Scotland"));
    assertEquals("SCOTTISH", REGIONS.regionForAccent("Scottish by birth, southern by upbringing"));
  }

  @Test
  public void keywordsMatchWholeWordsCaseInsensitively() {
    assertEquals("SOUTHERN_ENGLISH", REGIONS.regionForAccent("Received Pronunciation"));
    assertNull(REGIONS.regionForAccent("Irishman-ish"));
  }

  @Test
  public void anAccentNamingNoRegionHasNone() {
    assertNull(REGIONS.regionForAccent("Strong Welsh accent, Welsh English pronunciation"));
    assertNull(REGIONS.regionForAccent(null));
  }

  @Test
  public void theBundledKeywordsSendNorthernIrishToIrishAndTheDefaultToSouthernEnglish() {
    GeminiVoiceRegions bundled = GeminiVoiceRegions.bundled();
    assertEquals(
        "IRISH",
        bundled.regionForAccent("Strong Northern Irish accent, Irish English pronunciation"));
    assertEquals(
        "SOUTHERN_ENGLISH",
        bundled.regionForAccent(
            "Strong friendly, down-to-earth southern English accent, British English pronunciation"));
    assertEquals(
        "NORSE",
        bundled.regionForAccent(
            "Strong Norse Scandinavian accent, Scandinavian-accented English pronunciation"));
    assertEquals(
        "INDIAN_ENGLISH",
        bundled.regionForAccent("Strong Indian accent, Indian English pronunciation"));
    assertEquals("INDIAN_ENGLISH", bundled.regionForAccent("Strong accent from south India"));
    assertEquals("INDIAN_ENGLISH", bundled.regionForAccent("Strong West Indian accent"));
    assertNull(bundled.regionForAccent("Strong Southern American accent"));
  }

  @Test
  public void theDefaultCompanionAccentNamesSouthernEnglish() {
    VoicedDialogueConfig defaults = mock(VoicedDialogueConfig.class, Answers.CALLS_REAL_METHODS);
    assertEquals(
        "SOUTHERN_ENGLISH",
        GeminiVoiceRegions.bundled().regionForAccent(defaults.followerAccent()));
  }

  @Test
  public void theBundledTrollPoolHoldsOnlyTheTwoDeepestSouthernEnglishMen() {
    GeminiVoiceRegions bundled = GeminiVoiceRegions.bundled();
    Set<String> voiced = new HashSet<>();
    for (int seed = 0; seed < 200; seed++) {
      voiced.add(bundled.voiceFor("DEEP_SOUTHERN_ENGLISH", NpcGender.MALE, seed));
    }
    assertEquals(new HashSet<>(Arrays.asList("en-gb-advisor-8", "en-gb-assistant-2")), voiced);
  }

  @Test
  public void noChildSharesAVoiceWithTheTrolls() {
    GeminiVoiceRegions bundled = GeminiVoiceRegions.bundled();
    Set<String> trolls = new HashSet<>();
    for (int seed = 0; seed < 200; seed++) {
      trolls.add(bundled.voiceFor("DEEP_SOUTHERN_ENGLISH", NpcGender.MALE, seed));
    }
    for (String region : new String[] {"DEEP_SOUTHERN_ENGLISH", "SOUTHERN_ENGLISH"}) {
      for (int seed = 0; seed < 200; seed++) {
        String child = bundled.childVoiceFor(region, NpcGender.MALE, seed);
        assertNotNull(region, child);
        assertFalse(region, trolls.contains(child));
      }
    }
  }

  @Test
  public void theBundledIndianEnglishPoolsHoldOnlyIndianEnglishVoices() {
    GeminiVoiceRegions bundled = GeminiVoiceRegions.bundled();
    Set<String> readAsBritish =
        new HashSet<>(Arrays.asList("en-in-advisor-9", "en-in-assistant-10", "en-in-concierge-1"));
    for (NpcGender gender : new NpcGender[] {NpcGender.MALE, NpcGender.FEMALE}) {
      for (int seed = 0; seed < 200; seed++) {
        String adult = bundled.voiceFor("INDIAN_ENGLISH", gender, seed);
        String child = bundled.childVoiceFor("INDIAN_ENGLISH", gender, seed);
        assertTrue(adult, adult.startsWith("en-in-"));
        assertTrue(child, child.startsWith("en-in-"));
        assertFalse(adult, readAsBritish.contains(adult));
        assertFalse(child, readAsBritish.contains(child));
      }
    }
  }

  @Test
  public void anEmptyTableVoicesNothing() {
    GeminiVoiceRegions empty = new GeminiVoiceRegions(new JsonObject());
    assertNull(empty.voiceFor("IRISH", NpcGender.MALE, 1));
    assertNull(empty.regionForAccent("Irish"));
  }

  @Test
  public void theBundledTableCarriesNativeVoicesForEachGender() {
    GeminiVoiceRegions bundled = GeminiVoiceRegions.bundled();
    for (String region :
        new String[] {
          "IRISH", "SCOTTISH", "SOUTHERN_ENGLISH", "ITALIAN", "NORSE", "INDIAN_ENGLISH"
        }) {
      assertNotNull(region, bundled.voiceFor(region, NpcGender.MALE, 1));
      assertNotNull(region, bundled.voiceFor(region, NpcGender.FEMALE, 1));
    }
    assertFalse(bundled.voiceFor("IRISH", NpcGender.MALE, 1).isEmpty());
  }

  private static final GeminiVoiceRegions AGED =
      new GeminiVoiceRegions(
          new JsonParser()
              .parse(
                  "{\"SE\":{\"MALE\":[\"m25\",\"m28\",\"m32\",\"m35\",\"m46\",\"m49\",\"m55\","
                      + "\"m-unknown\"]}}")
              .getAsJsonObject(),
          null,
          ages());

  private static java.util.Map<String, Integer> ages() {
    java.util.Map<String, Integer> ages = new java.util.HashMap<>();
    ages.put("m25", 25);
    ages.put("m28", 28);
    ages.put("m32", 32);
    ages.put("m35", 35);
    ages.put("m46", 46);
    ages.put("m49", 49);
    ages.put("m55", 55);
    return ages;
  }

  @Test
  public void anOldNpcTakesTheOldestVoicesWhenNoneAreWithinTheWindow() {
    Set<String> voices = new HashSet<>();
    for (int seed = 0; seed < 200; seed++) {
      voices.add(AGED.voiceFor("SE", NpcGender.MALE, seed, 70));
    }
    assertEquals(new HashSet<>(Arrays.asList("m46", "m49", "m55")), voices);
  }

  private Set<String> voicesAt(int age) {
    Set<String> voices = new HashSet<>();
    for (int seed = 0; seed < 300; seed++) {
      voices.add(AGED.voiceFor("SE", NpcGender.MALE, seed, age));
    }
    return voices;
  }

  @Test
  public void anAgeTakesEveryVoiceWithinTheWindow() {
    assertEquals(new HashSet<>(Arrays.asList("m25", "m28", "m32", "m35")), voicesAt(30));
  }

  @Test
  public void aVoiceWithNoKnownAgeNeverMatchesAnAge() {
    for (int age = 1; age <= 120; age += 7) {
      assertFalse(voicesAt(age).contains("m-unknown"));
    }
  }

  @Test
  public void noAgeKeepsTheSeededPickFromTheWholePool() {
    for (int seed = 0; seed < 50; seed++) {
      assertEquals(
          AGED.voiceFor("SE", NpcGender.MALE, seed),
          AGED.voiceFor("SE", NpcGender.MALE, seed, null));
    }
  }

  @Test
  public void aPoolWithNoKnownAgesIgnoresTheAge() {
    for (int seed = 0; seed < 50; seed++) {
      assertEquals(
          REGIONS.voiceFor("SCOTTISH", NpcGender.MALE, seed),
          REGIONS.voiceFor("SCOTTISH", NpcGender.MALE, seed, 70));
    }
  }

  @Test
  public void anAgedNpcStillKeepsOneVoiceForLife() {
    String first = AGED.voiceFor("SE", NpcGender.MALE, 3480, 65);
    for (int i = 0; i < 10; i++) {
      assertEquals(first, AGED.voiceFor("SE", NpcGender.MALE, 3480, 65));
    }
  }

  @Test
  public void bundledAgesAreReadFromTheRoot() {
    JsonObject root =
        new JsonParser()
            .parse("{\"voiceAges\":{\"a\":40,\"b\":61,\"c\":\"old\"}}")
            .getAsJsonObject();
    assertEquals(Integer.valueOf(61), GeminiVoiceRegions.ages(root).get("b"));
    assertFalse("a non-numeric age is skipped", GeminiVoiceRegions.ages(root).containsKey("c"));
    assertTrue(GeminiVoiceRegions.ages(new JsonObject()).isEmpty());
  }
}
