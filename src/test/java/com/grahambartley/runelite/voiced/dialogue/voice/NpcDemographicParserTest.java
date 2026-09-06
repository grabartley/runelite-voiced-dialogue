package com.grahambartley.runelite.voiced.dialogue.voice;

import static org.junit.Assert.assertEquals;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Maps raw wiki/learned race and gender strings onto the voice enums. */
@RunWith(JUnitParamsRunner.class)
public class NpcDemographicParserTest {

  private Object[] raceCases() {
    return new Object[] {
      // Exact enum names map directly.
      new Object[] {"DWARF", NpcRace.DWARF},
      new Object[] {"goblin", NpcRace.GOBLIN},
      // Keyword variants bucket onto the nearest race.
      new Object[] {"Old man", NpcRace.HUMAN},
      new Object[] {"Elven warrior", NpcRace.ELF},
      new Object[] {"Imcando dwarf", NpcRace.DWARF},
      new Object[] {"Gnome child", NpcRace.GOBLIN},
      new Object[] {"Mountain giant", NpcRace.TROLL},
      new Object[] {"Skeleton mage", NpcRace.UNDEAD},
      new Object[] {"Restless ghost", NpcRace.UNDEAD},
      new Object[] {"Green dragon", NpcRace.DEMON},
      new Object[] {"Jungle gorilla", NpcRace.GORILLA},
      new Object[] {"Karamja primate", NpcRace.MONKEY},
      new Object[] {"Battle mage", NpcRace.WIZARD},
      new Object[] {"Tortuga elder", NpcRace.TORTUGAN},
      new Object[] {"Icyene queen", NpcRace.ICYENE},
      new Object[] {"Arceuus", NpcRace.ARCEUUS},
      new Object[] {"Citizen of Arceuus", NpcRace.ARCEUUS},
      new Object[] {"Citizens of Arceuus", NpcRace.ARCEUUS},
      // A half-blood hits the human keyword arm first and stays HUMAN (Safalaan).
      new Object[] {"Half Icyene, half human", NpcRace.HUMAN},
      // Unknown or empty falls through to UNKNOWN.
      new Object[] {null, NpcRace.UNKNOWN},
      new Object[] {"", NpcRace.UNKNOWN},
      new Object[] {"Penguin", NpcRace.UNKNOWN},
    };
  }

  @Test
  @Parameters(method = "raceCases")
  public void mapsRawRaceToEnum(String raw, NpcRace expected) {
    assertEquals(expected, NpcDemographicParser.toRace(raw));
  }

  private Object[] genderCases() {
    return new Object[] {
      new Object[] {"FEMALE", NpcGender.FEMALE},
      new Object[] {"Woman", NpcGender.FEMALE},
      new Object[] {"Noble lady", NpcGender.FEMALE},
      new Object[] {"Old man", NpcGender.MALE},
      new Object[] {"Lord of the manor", NpcGender.MALE},
      // Empty is UNKNOWN; an unrecognised non-empty value defaults to MALE.
      new Object[] {null, NpcGender.UNKNOWN},
      new Object[] {"", NpcGender.UNKNOWN},
      new Object[] {"indeterminate", NpcGender.MALE},
    };
  }

  @Test
  @Parameters(method = "genderCases")
  public void mapsRawGenderToEnum(String raw, NpcGender expected) {
    assertEquals(expected, NpcDemographicParser.toGender(raw));
  }
}
