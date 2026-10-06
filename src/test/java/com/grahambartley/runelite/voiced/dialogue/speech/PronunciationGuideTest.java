package com.grahambartley.runelite.voiced.dialogue.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.Test;

public class PronunciationGuideTest {

  private static final int BUNDLED_FLOOR = 100;

  private final PronunciationGuide bundled = PronunciationGuide.load();

  @Test
  public void theBundledTableLoads() {
    assertTrue(bundled.size() >= BUNDLED_FLOOR);
  }

  @Test
  public void everyBundledWordRespellsToItsOwnSaying() throws Exception {
    for (JsonElement element : bundledRoot().getAsJsonArray("words")) {
      JsonObject row = element.getAsJsonObject();
      String word = row.get("word").getAsString();
      String say = row.get("say").getAsString().toLowerCase(Locale.ROOT);
      String expected = Character.toUpperCase(say.charAt(0)) + say.substring(1);
      assertEquals(word, expected, bundled.respell(word));
    }
  }

  @Test
  public void aListedNameIsRespelledInsideALine() {
    assertEquals(
        "I come from Yah-tiz-so, while my wife is from Nay-tiz-not.",
        bundled.respell("I come from Jatizso, while my wife is from Neitiznot."));
  }

  @Test
  public void aNameWrittenInLowerCaseStaysLowerCase() {
    assertEquals("off to nay-tiz-not", bundled.respell("off to neitiznot"));
  }

  @Test
  public void aNameWrittenInCapitalsIsMatchedRegardlessOfCase() {
    assertEquals("WELCOME TO Ar-doyn!", bundled.respell("WELCOME TO ARDOUGNE!"));
  }

  @Test
  public void aPossessiveKeepsItsEnding() {
    assertEquals("Sa-ra-dome-in's power", bundled.respell("Saradomin's power"));
  }

  @Test
  public void aNameAcrossTwoWordsMatchesAnyRunOfSpaces() {
    assertEquals("Al ka-rid is hot", bundled.respell("Al  Kharid is hot"));
  }

  @Test
  public void aCurlyApostropheMatchesAStraightOne() {
    assertEquals("Mor-ton is quiet", bundled.respell("Mort’ton is quiet"));
  }

  @Test
  public void theLongestListedNameWins() {
    assertEquals("Eh-lid-in-iss of the Eh-lid", bundled.respell("Elidinis of the Elid"));
  }

  @Test
  public void aNameInsideALongerWordIsLeftAlone() {
    assertEquals("Serenity and Guthixian", bundled.respell("Serenity and Guthixian"));
  }

  @Test
  public void anUnlistedLineIsReturnedUnchanged() {
    assertEquals("Seek out Islwyn and Eluned.", bundled.respell("Seek out Islwyn and Eluned."));
  }

  @Test
  public void nullStaysNull() {
    assertNull(bundled.respell(null));
  }

  @Test
  public void anEmptyGuideChangesNothing() {
    assertEquals("Neitiznot", PronunciationGuide.EMPTY.respell("Neitiznot"));
  }

  @Test
  public void parseReadsEveryRow() {
    PronunciationGuide guide = PronunciationGuide.parse(table(row("Zanaris", "zuh-NAR-iss")));

    assertEquals(1, guide.size());
    assertEquals("Zuh-nar-iss", guide.respell("Zanaris"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void parseRejectsAWordListedTwiceInAnyCase() {
    PronunciationGuide.parse(table(row("Zanaris", "zuh-NAR-iss"), row("zanaris", "ZAN-ar-iss")));
  }

  @Test(expected = IllegalArgumentException.class)
  public void parseRejectsARowWithoutASaying() {
    PronunciationGuide.parse(table(row("Zanaris", " ")));
  }

  @Test(expected = IllegalArgumentException.class)
  public void parseRejectsARowWithoutAWord() {
    JsonObject row = new JsonObject();
    row.addProperty("say", "zuh-NAR-iss");
    PronunciationGuide.parse(table(row));
  }

  private static JsonObject bundledRoot() throws Exception {
    try (InputStreamReader reader =
        new InputStreamReader(
            PronunciationGuide.class.getResourceAsStream(PronunciationGuide.RESOURCE),
            StandardCharsets.UTF_8)) {
      return new JsonParser().parse(reader).getAsJsonObject();
    }
  }

  private static JsonObject row(String word, String say) {
    JsonObject row = new JsonObject();
    row.addProperty("word", word);
    row.addProperty("say", say);
    return row;
  }

  private static JsonObject table(JsonObject... rows) {
    JsonArray words = new JsonArray();
    for (JsonObject row : rows) {
      words.add(row);
    }
    JsonObject root = new JsonObject();
    root.add("words", words);
    return root;
  }
}
