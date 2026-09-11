package com.grahambartley.runelite.voiced.dialogue.speaker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pins the whole race pipeline both stages share: raw wiki race text to the bucket name stored in
 * the tables, and that bucket name on to the race that picks the voice.
 */
@RunWith(JUnitParamsRunner.class)
public class RaceBucketTest {

  private Object[] wikiRaceCases() {
    return new Object[] {
      new Object[] {"Human", "Human", NpcRace.HUMAN},
      new Object[] {"Gnome", "Gnome", NpcRace.GOBLIN},
      new Object[] {"Goblin", "Goblin", NpcRace.GOBLIN},
      new Object[] {"Hobgoblin", "Goblin", NpcRace.GOBLIN},
      new Object[] {"Dwarf", "Dwarf", NpcRace.DWARF},
      new Object[] {"Dwarven", "Dwarf", NpcRace.DWARF},
      new Object[] {"Elf", "Elf", NpcRace.ELF},
      new Object[] {"Elven", "Elf", NpcRace.ELF},
      new Object[] {"Troll", "Troll", NpcRace.TROLL},
      new Object[] {"Giant", "Troll", NpcRace.TROLL},
      new Object[] {"Ogre", "Troll", NpcRace.TROLL},
      new Object[] {"Monkey", "Monkey", NpcRace.MONKEY},
      new Object[] {"Gorilla", "Monkey", NpcRace.MONKEY},
      new Object[] {"Wizard", "Wizard", NpcRace.WIZARD},
      new Object[] {"Mage", "Wizard", NpcRace.WIZARD},
      new Object[] {"Zombie", "Undead", NpcRace.UNDEAD},
      new Object[] {"Skeleton", "Undead", NpcRace.UNDEAD},
      new Object[] {"Vampyre", "Undead", NpcRace.UNDEAD},
      new Object[] {"Ghost", "Undead", NpcRace.UNDEAD},
      new Object[] {"Aranei", "Aranei", NpcRace.ARANEI},
      new Object[] {"Dog", "Dog", NpcRace.DOG},
      new Object[] {"Dogs", "Dog", NpcRace.DOG},
      new Object[] {"Demon", "Demon", NpcRace.DEMON},
      new Object[] {"Dragon", "Demon", NpcRace.DEMON},
      new Object[] {"Imp", "Demon", NpcRace.DEMON},
      // Case and surrounding words do not matter, and the distinctive race wins over "human".
      new Object[] {"GHOST", "Undead", NpcRace.UNDEAD},
      new Object[] {"Human/Elf hybrid", "Elf", NpcRace.ELF},
      new Object[] {"[[Aranei]]", "Aranei", NpcRace.ARANEI},
      new Object[] {"[[Dog]]", "Dog", NpcRace.DOG},
    };
  }

  @Test
  @Parameters(method = "wikiRaceCases")
  public void wikiRaceTextBucketsAndThenVoices(String wikiText, String bucket, NpcRace race) {
    RaceBucket matched = RaceBucket.forWikiText(wikiText);
    assertEquals("bucket for '" + wikiText + "'", bucket, matched.bucketName());
    assertEquals("race for bucket '" + bucket + "'", race, NpcDemographicParser.toRace(bucket));
  }

  private Object[] storedRaceKeywordCases() {
    return new Object[] {
      new Object[] {"Dog", NpcRace.DOG},
      new Object[] {"Guard dog", NpcRace.DOG},
      // A hound dragged back from the grave or out of the abyss is that before it is a dog.
      new Object[] {"Undead dog", NpcRace.UNDEAD},
      new Object[] {"Demonic dog", NpcRace.DEMON},
    };
  }

  @Test
  @Parameters(method = "storedRaceKeywordCases")
  public void aStoredRaceStringVoicesByItsMostDistinctiveKeyword(String stored, NpcRace race) {
    assertEquals(
        "race for stored text '" + stored + "'", race, NpcDemographicParser.toRace(stored));
  }

  @Test
  public void raceTextTheWikiTableDoesNotKnowMatchesNoBucket() {
    assertNull(RaceBucket.forWikiText("Penguin"));
  }

  private Object[] storedOnlyBucketCases() {
    return new Object[] {
      new Object[] {"Arceuus", NpcRace.ARCEUUS},
      new Object[] {"Gorilla", NpcRace.GORILLA},
      new Object[] {"Tortugan", NpcRace.TORTUGAN},
      new Object[] {"Icyene", NpcRace.ICYENE},
    };
  }

  @Test
  @Parameters(method = "storedOnlyBucketCases")
  public void bucketsTheWikiNeverEmitsStillVoiceFromTheTables(String bucket, NpcRace race) {
    assertEquals(race, NpcDemographicParser.toRace(bucket));
  }
}
