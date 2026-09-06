package com.grahambartley.runelite.voiced.dialogue.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.data.NpcProfileLayers.CategoryRule;
import org.junit.Test;

/** Parsing the bundled {@code profiles} section into layers. */
public class NpcProfileParserTest {

  private static NpcProfileLayers parse(String json) {
    return NpcProfileParser.parse(new JsonParser().parse(json).getAsJsonObject());
  }

  private static final String COMPLETE_DEFAULT =
      "\"default\":{\"name\":\"D\",\"accent\":\"A\",\"style\":\"S\",\"pace\":\"P\"}";

  @Test
  public void parsesTheCompleteDefault() {
    NpcProfileLayers layers = parse("{" + COMPLETE_DEFAULT + "}");
    assertEquals("D", layers.defaultProfile().name());
    assertEquals("A", layers.defaultProfile().accent());
    assertEquals("S", layers.defaultProfile().style());
    assertEquals("P", layers.defaultProfile().pace());
  }

  @Test
  public void anIncompleteDefaultFallsBackToTheBuiltInOne() {
    NpcProfileLayers layers = parse("{\"default\":{\"name\":\"D\"}}");
    assertEquals(NpcProfileLayers.BUILTIN_DEFAULT, layers.defaultProfile());
  }

  @Test
  public void aMissingDefaultFallsBackToTheBuiltInOne() {
    assertEquals(NpcProfileLayers.BUILTIN_DEFAULT, parse("{}").defaultProfile());
  }

  @Test
  public void layerMapsAreKeyedLowerCaseAndSkipComments() {
    NpcProfileLayers layers =
        parse(
            "{\"byRace\":{\"Troll\":{\"accent\":\"Brixton.\"},\"_comment\":{\"accent\":\"x\"}},"
                + "\"byEthnicity\":{\"Kharidian\":{\"accent\":\"Middle Eastern.\"}}}");

    assertEquals("Brixton.", layers.byRace().get("troll").accent());
    assertNull("a comment key is not a layer", layers.byRace().get("_comment"));
    assertEquals("Middle Eastern.", layers.byEthnicity().get("kharidian").accent());
  }

  @Test
  public void sparseLayersLeaveUnsetFieldsNullSoTheyInherit() {
    NpcProfileLayers layers = parse("{\"player\":{\"style\":\"Brave hero.\"}}");
    assertEquals("Brave hero.", layers.playerLayer().style());
    assertNull(layers.playerLayer().name());
    assertNull(layers.playerLayer().accent());
    assertNull(layers.playerLayer().pace());
  }

  @Test
  public void anEmptyStringFieldIsTreatedAsAbsent() {
    NpcProfileLayers layers = parse("{\"player\":{\"accent\":\"\"}}");
    assertNull(layers.playerLayer().accent());
  }

  @Test
  public void categoriesKeepDeclarationOrderAndLowerCasedKeywords() {
    NpcProfileLayers layers =
        parse(
            "{\"byCategory\":["
                + "{\"id\":\"vampyre\",\"keywords\":[\"Vampyre\"],\"accent\":\"Transylvanian.\"},"
                + "{\"id\":\"child\",\"keywords\":[\"Child\"],\"lifeStage\":\"child\"}]}");

    assertEquals(2, layers.byCategory().size());
    CategoryRule vampyre = layers.byCategory().get(0);
    assertEquals("vampyre", vampyre.id());
    assertEquals("vampyre", vampyre.keywords().get(0));
    assertEquals("Transylvanian.", vampyre.layer().accent());
    assertTrue("the child marker rides on the rule", layers.byCategory().get(1).child());
  }

  @Test
  public void categoriesWithoutKeywordsAreSkippedAndAnIdIsOptional() {
    NpcProfileLayers layers =
        parse(
            "{\"byCategory\":[{\"accent\":\"orphan\"},\"not an object\","
                + "{\"keywords\":[\"imp\"],\"style\":\"Squeaky.\"}]}");

    assertEquals(1, layers.byCategory().size());
    assertEquals("category", layers.byCategory().get(0).id());
  }

  @Test
  public void byIdSkipsCommentsAndNonNumericKeys() {
    NpcProfileLayers layers =
        parse(
            "{\"byId\":{\"_comment\":\"x\",\"nope\":{\"name\":\"N\"},\"100\":{\"name\":\"Vanstrom\"}}}");

    assertEquals(1, layers.byId().size());
    assertEquals("Vanstrom", layers.byId().get(100).name());
  }

  @Test
  public void theBundledResourceParses() {
    assertNotNull(NpcProfileParser.loadResource("/npc-voices.json"));
  }

  @Test
  public void aMissingResourceIsAnAbsentSection() {
    assertNull(NpcProfileParser.loadResource("/no-such-resource.json"));
  }

  @Test
  public void aResourceWithoutAProfilesSectionIsAbsent() {
    assertNull(NpcProfileParser.loadResource("/expression-emotions.json"));
  }

  @Test
  public void anEmptySectionParsesToEmptyLayers() {
    NpcProfileLayers layers = parse("{}");
    assertTrue(layers.byRace().isEmpty());
    assertTrue(layers.byEthnicity().isEmpty());
    assertTrue(layers.byCategory().isEmpty());
    assertTrue(layers.byId().isEmpty());
    assertNull(layers.playerLayer());
  }
}
