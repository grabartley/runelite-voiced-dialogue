package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcDemographicParser;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class WikiMappingTest {

  private final WikiMapping mapping = WikiMapping.get();

  private Object[] wikiRaceCases() {
    return new Object[] {
      new Object[] {"Human", "Human"},
      new Object[] {"[[Gnome]]", "Gnome"},
      new Object[] {"Hobgoblin", "Goblin"},
      new Object[] {"Dwarven", "Dwarf"},
      new Object[] {"Elves", "Elf"},
      new Object[] {"Ogre", "Troll"},
      new Object[] {"Cyclops", "Troll"},
      new Object[] {"Gorilla", "Gorilla"},
      new Object[] {"Monkey", "Monkey"},
      new Object[] {"Vampyre", "Undead"},
      new Object[] {"GHOST", "Undead"},
      new Object[] {"Dragon", "Demon"},
      new Object[] {"TzHaar", "Demon"},
      new Object[] {"Dogs", "Dog"},
      new Object[] {"Crab", "Crab"},
      new Object[] {"Penguins", "Penguin"},
      new Object[] {"Citizens of Arceuus", "Arceuus"},
      new Object[] {"Aranei", "Aranei"},
      new Object[] {"Tortugan", "Tortugan"},
      new Object[] {"Icyene", "Icyene"},
      new Object[] {"Half Icyene", "Human"},
      new Object[] {"Merfolk", "Human"},
    };
  }

  @Test
  @Parameters(method = "wikiRaceCases")
  public void wikiRaceTextBucketsTheSameWayTheGeneratorDoes(String wikiText, String bucket) {
    assertEquals("bucket for '" + wikiText + "'", bucket, mapping.raceForWikiText(wikiText));
  }

  @Test
  public void anAbsentRaceFieldHasNoBucketSoCategoriesCanAnswer() {
    assertNull(mapping.raceForWikiText(null));
    assertNull(mapping.raceForWikiText(""));
  }

  @Test
  public void gorillaBeatsMonkeySoApesStayOffTheIslandVoice() {
    assertEquals("Gorilla", mapping.raceForWikiText("Gorilla"));
  }

  private Object[] categoryCases() {
    return new Object[] {
      new Object[] {"Category:Trolls", "Troll"},
      new Object[] {"Category:Ghosts", "Undead"},
      new Object[] {"Category:TzHaar", "Demon"},
      new Object[] {"Category:Wizards", "Wizard"},
      new Object[] {"Category:Penguins", "Penguin"},
      new Object[] {"Category:Humans", "Human"},
    };
  }

  @Test
  @Parameters(method = "categoryCases")
  public void categoriesCarryTheRaceWhenTheInfoboxDoesNot(String category, String bucket) {
    assertEquals(bucket, mapping.raceForCategories(singletonList(category)));
  }

  @Test
  public void categoriesWithNoRaceKeywordAnswerNothing() {
    assertNull(mapping.raceForCategories(singletonList("Category:Quest NPCs")));
    assertNull(mapping.raceForCategories(Collections.<String>emptyList()));
    assertNull(mapping.raceForCategories(null));
  }

  @Test
  public void leagueRegionMapsOntoAnEthnicityAccent() {
    assertEquals("fremennik", mapping.ethnicityKey("Fremennik", null, null));
    assertEquals("varlamore", mapping.ethnicityKey("Varlamore", null, null));
    assertEquals("morytania", mapping.ethnicityKey("Morytania", null, null));
  }

  @Test
  public void multiRegionAndUnmappedRegionsCarryNoSingleEthnicity() {
    assertNull(mapping.ethnicityKey("Desert, Misthalin", null, null));
    assertNull(mapping.ethnicityKey("Asgarnia & Misthalin", null, null));
    assertNull(mapping.ethnicityKey("No", null, null));
    assertNull(mapping.ethnicityKey(null, null, null));
  }

  @Test
  public void theDesertSplitsOnLocationOrCategory() {
    assertEquals("kharidian", mapping.ethnicityKey("Desert", "Pollnivneach", null));
    assertEquals("menaphite", mapping.ethnicityKey("Desert", "Sophanem", null));
    assertEquals(
        "menaphite",
        mapping.ethnicityKey("Desert", "Unknown", Arrays.asList("Category:Menaphites")));
    assertEquals(
        "kharidian", mapping.ethnicityKey("Desert", null, Arrays.asList("Category:Bandits")));
  }

  @Test
  public void onlyNpcAndMonsterPagesAreWorthReading() {
    assertTrue(mapping.isNpcPage("{{Infobox NPC\n|race=Human\n}}"));
    assertTrue(mapping.isNpcPage("{{infobox_monster\n}}"));
    assertTrue(mapping.isNpcPage("{{Multi Infobox\n|item1={{Infobox NPC\n}}\n}}"));
    assertFalse(mapping.isNpcPage("{{Infobox Item\n|name=Bucket\n}}"));
    assertFalse(mapping.isNpcPage(null));
  }

  @Test
  public void genderTextNormalisesToTheStoredValues() {
    assertEquals("Female", mapping.genderForWikiText("female"));
    assertEquals("Male", mapping.genderForWikiText("Male"));
    assertEquals("Male", mapping.genderForWikiText("Unknown"));
    assertEquals("Male", mapping.genderForWikiText(null));
  }

  @Test
  public void everyDeclaredRaceVoicesFromTheRuntimeTables() {
    for (JsonElement race : mappingResource().getAsJsonArray("races")) {
      assertNotEquals(
          "voice race for declared race '" + race.getAsString() + "'",
          NpcRace.UNKNOWN,
          NpcDemographicParser.toRace(race.getAsString()));
    }
  }

  @Test
  public void everyRuleAnswersWithARaceTheOverridesAccept() {
    JsonObject resource = mappingResource();
    List<String> declared = new ArrayList<>();
    for (JsonElement race : resource.getAsJsonArray("races")) {
      declared.add(race.getAsString());
    }
    for (String rules : new String[] {"raceRules", "categoryRaceRules"}) {
      for (JsonElement rule : resource.getAsJsonArray(rules)) {
        String race = rule.getAsJsonObject().get("race").getAsString();
        assertTrue("'" + race + "' is a declared race", declared.contains(race));
      }
    }
    assertTrue(declared.contains(mapping.raceForWikiText("Ogre")));
    assertTrue(declared.contains(mapping.raceForCategories(singletonList("Category:Wizards"))));
  }

  private static JsonObject mappingResource() {
    return new JsonParser()
        .parse(
            new InputStreamReader(
                WikiMapping.class.getResourceAsStream("/wiki-mapping.json"),
                StandardCharsets.UTF_8))
        .getAsJsonObject();
  }
}
