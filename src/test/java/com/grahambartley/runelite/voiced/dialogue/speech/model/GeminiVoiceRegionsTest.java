package com.grahambartley.runelite.voiced.dialogue.speech.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

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
  public void anEmptyTableVoicesNothing() {
    GeminiVoiceRegions empty = new GeminiVoiceRegions(new JsonObject());
    assertNull(empty.voiceFor("IRISH", NpcGender.MALE, 1));
    assertNull(empty.regionForAccent("Irish"));
  }

  @Test
  public void theBundledTableCarriesNativeVoicesForEachGender() {
    GeminiVoiceRegions bundled = GeminiVoiceRegions.bundled();
    for (String region : new String[] {"IRISH", "SCOTTISH", "SOUTHERN_ENGLISH", "ITALIAN"}) {
      assertNotNull(region, bundled.voiceFor(region, NpcGender.MALE, 1));
      assertNotNull(region, bundled.voiceFor(region, NpcGender.FEMALE, 1));
    }
    assertFalse(bundled.voiceFor("IRISH", NpcGender.MALE, 1).isEmpty());
  }
}
