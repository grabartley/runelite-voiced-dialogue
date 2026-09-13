package com.grahambartley.runelite.voiced.dialogue.speaker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    assertEquals("Dwarf (race)", infobox.race());
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
  public void categoriesRideAlongWithTheInfobox() {
    assertEquals(
        Collections.singletonList("Category:Trolls"),
        WikiInfobox.parse("{{Infobox Monster\n}}", Collections.singletonList("Category:Trolls"))
            .categories());
    assertTrue(WikiInfobox.parse("{{Infobox Monster\n}}", null).categories().isEmpty());
  }

  @Test
  public void onlyNpcAndMonsterPagesAreWorthReading() {
    assertTrue(WikiInfobox.isNpcPage("{{Infobox NPC\n|race=Human\n}}"));
    assertTrue(WikiInfobox.isNpcPage("{{infobox monster\n}}"));
    assertTrue(WikiInfobox.isNpcPage("{{Multi Infobox\n|item1={{Infobox NPC\n}}\n}}"));
    assertFalse(WikiInfobox.isNpcPage("{{Infobox Item\n|name=Bucket\n}}"));
    assertFalse(WikiInfobox.isNpcPage(null));
  }
}
