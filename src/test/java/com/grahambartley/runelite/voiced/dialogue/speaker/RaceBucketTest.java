package com.grahambartley.runelite.voiced.dialogue.speaker;

import static org.junit.Assert.assertEquals;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class RaceBucketTest {

  private Object[] storedRaceKeywordCases() {
    return new Object[] {
      new Object[] {"Undead dog", NpcRace.UNDEAD},
      new Object[] {"Demonic dog", NpcRace.DEMON},
      new Object[] {"Hellhound", NpcRace.DEMON},
      new Object[] {"Revenant hellhound", NpcRace.UNDEAD},
      new Object[] {"Undead crab", NpcRace.UNDEAD},
      new Object[] {"Demonic penguin", NpcRace.DEMON},
      new Object[] {"Sand crab", NpcRace.CRAB},
    };
  }

  @Test
  @Parameters(method = "storedRaceKeywordCases")
  public void aStoredRaceStringVoicesByItsMostDistinctiveKeyword(String stored, NpcRace race) {
    assertEquals(
        "race for stored text '" + stored + "'", race, NpcDemographicParser.toRace(stored));
  }

  private Object[] bucketNameCases() {
    return new Object[] {
      new Object[] {"Arceuus", NpcRace.ARCEUUS},
      new Object[] {"Gorilla", NpcRace.GORILLA},
      new Object[] {"Tortugan", NpcRace.TORTUGAN},
      new Object[] {"Icyene", NpcRace.ICYENE},
    };
  }

  @Test
  @Parameters(method = "bucketNameCases")
  public void aBucketNameVoicesFromTheTables(String bucket, NpcRace race) {
    assertEquals(race, NpcDemographicParser.toRace(bucket));
  }
}
