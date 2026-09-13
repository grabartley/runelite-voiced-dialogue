package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class WikiInfoboxTest {

  private static final String SWITCH_PAGE =
      "{{Infobox NPC\n"
          + "|race = [[Human]]\n"
          + "|gender1 = Male\n"
          + "|gender2 = Female\n"
          + "|id1 = 3010, 3011\n"
          + "|id2 = 3012\n"
          + "|leagueRegion = Asgarnia\n"
          + "}}";

  @Test
  public void fieldsAreStrippedOfWikiMarkup() {
    WikiInfobox infobox =
        WikiInfobox.parse(
            "{{Infobox NPC\n|race = [[Dwarf (race)]]<ref>a ref</ref>\n|location = ''Keldagrim''\n}}",
            null);
    assertEquals(Collections.singletonList("Dwarf (race)"), infobox.raceReadings());
    assertEquals("''Keldagrim''", infobox.location());
  }

  @Test
  public void eachVersionKeepsItsOwnGender() {
    WikiInfobox infobox = WikiInfobox.parse(SWITCH_PAGE, null);
    assertEquals("Male", infobox.genderForVersion(3010));
    assertEquals("Male", infobox.genderForVersion(3011));
    assertEquals("Female", infobox.genderForVersion(3012));
  }

  @Test
  public void anIdTheVersionsDoNotListFallsBackToTheFirstGender() {
    assertEquals("Male", WikiInfobox.parse(SWITCH_PAGE, null).genderForVersion(99999));
  }

  @Test
  public void gendersThatDoNotPairWithTheIdGroupsAllUseTheFirst() {
    WikiInfobox infobox =
        WikiInfobox.parse("{{Infobox NPC\n|gender = Female\n|id1 = 1\n|id2 = 2\n}}", null);
    assertEquals("Female", infobox.genderForVersion(1));
    assertEquals("Female", infobox.genderForVersion(2));
  }

  @Test
  public void aPageWithoutAGenderFieldAnswersNothing() {
    assertNull(WikiInfobox.parse("{{Infobox NPC\n|race = Human\n}}", null).genderForVersion(1));
  }

  @Test
  public void aPipedRaceReadsItsTargetBeforeItsDisplayText() {
    assertEquals(
        Arrays.asList("Dog_(disambiguation)", "Dog"),
        WikiInfobox.parse("{{Infobox NPC\n|race = [[Dog_(disambiguation)|Dog]]\n}}", null)
            .raceReadings());
  }

  @Test
  public void aPipedLeagueRegionAndLocationReadTheirTargets() {
    WikiInfobox infobox =
        WikiInfobox.parse(
            "{{Infobox NPC\n|leagueRegion = [[Kandarin|the Kandarin region]]\n"
                + "|location = [[Ardougne|East Ardougne]]\n}}",
            null);
    assertEquals("Kandarin", infobox.leagueRegion());
    assertEquals("Ardougne", infobox.location());
  }

  @Test
  public void aSingleLineSwitchInfoboxKeepsEveryVersion() {
    WikiInfobox infobox =
        WikiInfobox.parse(
            "{{Infobox NPC|id1=10438|gender1=Male|id2=10439|gender2=Female"
                + "|race=[[Dog_(disambiguation)|Dog]]}}",
            null);
    assertEquals("Male", infobox.genderForVersion(10438));
    assertEquals("Female", infobox.genderForVersion(10439));
    assertEquals(Arrays.asList("Dog_(disambiguation)", "Dog"), infobox.raceReadings());
  }

  @Test
  public void aTemplatedValueIsNotCutAtItsInnerPipe() {
    assertEquals(
        "and Draynor",
        WikiInfobox.parse(
                "{{Infobox NPC\n|location = {{plink|Lumbridge}} and [[Draynor|the village]]\n}}",
                null)
            .location());
  }

  @Test
  public void aStrayCloseEndsTheValueRatherThanSwallowingTheNextParameter() {
    WikiInfobox infobox = WikiInfobox.parse("{{Infobox NPC|race=Dog]]|gender=Female|id=1}}", null);
    assertEquals(Collections.singletonList("Dog"), infobox.raceReadings());
    assertEquals("Female", infobox.genderForVersion(1));
  }

  @Test
  public void categoriesRideAlongWithTheInfobox() {
    assertEquals(
        Collections.singletonList("Category:Trolls"),
        WikiInfobox.parse("{{Infobox Monster\n}}", Collections.singletonList("Category:Trolls"))
            .categories());
    assertTrue(WikiInfobox.parse("{{Infobox Monster\n}}", null).categories().isEmpty());
  }
}
