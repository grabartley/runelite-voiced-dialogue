package com.grahambartley.runelite.voiced.dialogue.speaker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonParser;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class NpcEntriesReaderTest {

  private final List<String> skipped = new ArrayList<>();

  private Map<Integer, NpcAttributes> read(String json) {
    return NpcEntriesReader.read(
        new StringReader(json), AttributeSource.STATIC_TABLE, (key, e) -> skipped.add(key));
  }

  @Test
  public void readsRaceGenderEthnicityAndLifeStage() {
    Map<Integer, NpcAttributes> entries =
        read(
            "{\"npcs\":{\"7\":{\"race\":\"Elf\",\"gender\":\"Female\",\"ethnicity\":\"tirannwn\","
                + "\"lifeStage\":\"child\"}}}");

    NpcAttributes a = entries.get(7);
    assertEquals("Elf", a.getRace());
    assertEquals("Female", a.getGender());
    assertEquals("tirannwn", a.getEthnicity());
    assertEquals(AttributeSource.STATIC_TABLE, a.getSource());
    assertEquals(7, a.getNpcId());
    assertTrue("the life-stage marker rides along", a.isChild());
  }

  @Test
  public void readsEachVariantIdsMainId() {
    Map<Integer, Integer> aliases =
        NpcEntriesReader.readAliases(
            new JsonParser()
                .parse("{\"aliases\":{\"16537\":16486,\"15255\":15253}}")
                .getAsJsonObject(),
            (key, e) -> skipped.add(key));

    assertEquals(Integer.valueOf(16486), aliases.get(16537));
    assertEquals(Integer.valueOf(15253), aliases.get(15255));
    assertEquals(2, aliases.size());
  }

  @Test
  public void aTableWithoutAliasesHasNone() {
    assertTrue(
        NpcEntriesReader.readAliases(
                new JsonParser().parse("{\"npcs\":{}}").getAsJsonObject(),
                (key, e) -> skipped.add(key))
            .isEmpty());
  }

  @Test
  public void aMalformedAliasIsSkippedAndTheRestRead() {
    Map<Integer, Integer> aliases =
        NpcEntriesReader.readAliases(
            new JsonParser()
                .parse("{\"aliases\":{\"x\":1,\"16537\":\"y\",\"15255\":15253}}")
                .getAsJsonObject(),
            (key, e) -> skipped.add(key));

    assertEquals(Collections.singletonMap(15255, 15253), aliases);
    assertEquals(Arrays.asList("x", "16537"), skipped);
  }

  @Test
  public void absentOptionalFieldsStayNull() {
    NpcAttributes a = read("{\"npcs\":{\"7\":{\"race\":\"Human\",\"gender\":\"Male\"}}}").get(7);
    assertNull(a.getEthnicity());
    assertNull(a.getLifeStage());
  }

  @Test
  public void jsonNullOptionalFieldsStayNull() {
    NpcAttributes a =
        read("{\"npcs\":{\"7\":{\"race\":\"Human\",\"gender\":\"Male\",\"ethnicity\":null,"
                + "\"lifeStage\":null}}}")
            .get(7);
    assertNull(a.getEthnicity());
    assertNull(a.getLifeStage());
  }

  @Test
  public void aMalformedEntryIsReportedAndSkippedWithoutLosingTheRest() {
    Map<Integer, NpcAttributes> entries =
        read(
            "{\"npcs\":{\"notAnId\":{\"race\":\"Human\",\"gender\":\"Male\"},"
                + "\"8\":{\"gender\":\"Male\"},"
                + "\"9\":{\"race\":\"Human\",\"gender\":\"Male\"}}}");

    assertEquals("only the well-formed entry survives", 1, entries.size());
    assertEquals("Human", entries.get(9).getRace());
    assertEquals("both bad rows are reported", 2, skipped.size());
  }

  @Test
  public void aDocumentWithoutAnNpcsObjectReadsAsAbsent() {
    assertNull(read("{}"));
    assertNull(read("{\"npcs\":[]}"));
  }
}
