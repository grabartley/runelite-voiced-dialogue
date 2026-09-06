package com.grahambartley.runelite.voiced.dialogue.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Covers the cave-echo seams: {@link CaveEchoPolicy#isUndergroundPoint(WorldPoint)}, the coordinate
 * predicate behind underground detection; {@link CaveEchoPolicy#isUnderground()}, the client read
 * that feeds the gate; and {@link CaveEchoPolicy#shouldEcho()}, the gate itself.
 */
@RunWith(JUnitParamsRunner.class)
public class CaveEchoPolicyTest {

  private Object[] undergroundPointCases() {
    return new Object[] {
      new Object[] {new WorldPoint(3200, Constants.OVERWORLD_MAX_Y - 1, 0), false},
      new Object[] {new WorldPoint(3200, Constants.OVERWORLD_MAX_Y, 0), true},
      new Object[] {new WorldPoint(3200, 9000, 0), true},
    };
  }

  @Test
  @Parameters(method = "undergroundPointCases")
  public void undergroundPointGatesOnTheOverworldCeiling(WorldPoint point, boolean expected) {
    assertEquals(expected, CaveEchoPolicy.isUndergroundPoint(point));
  }

  @Test
  public void prifddinasIsCorrectedBackToSurfaceByTheMirror() {
    WorldPoint inPrifddinasBand = new WorldPoint(3256, 6055, 0);
    assertTrue(
        "the raw point is in the underground band",
        inPrifddinasBand.getY() >= Constants.OVERWORLD_MAX_Y);
    assertFalse(
        "Prifddinas must read as surface after the mirror correction",
        CaveEchoPolicy.isUndergroundPoint(inPrifddinasBand));
  }

  @Test
  public void playerOwnedHouseIsCarvedOutDespiteSittingInTheBand() {
    for (WorldPoint inPoh :
        new WorldPoint[] {new WorldPoint(1960, 7045, 0), new WorldPoint(1944, 7107, 0)}) {
      assertTrue(
          "the POH point is in a carved-out region",
          CaveEchoPolicy.POH_REGION_IDS.contains(inPoh.getRegionID()));
      assertTrue(
          "the raw point is in the underground band", inPoh.getY() >= Constants.OVERWORLD_MAX_Y);
      assertFalse(
          "the player-owned house must read as surface", CaveEchoPolicy.isUndergroundPoint(inPoh));
    }
  }

  @Test
  public void isUndergroundReadsTheLivePlayerLocation() {
    Client client = mock(Client.class);
    VoicedDialogueConfig config = mock(VoicedDialogueConfig.class);
    CaveEchoPolicy policy = new CaveEchoPolicy(client, config);

    when(client.getLocalPlayer()).thenReturn(null);
    assertFalse("no local player reads as surface", policy.isUnderground());

    Player player = mock(Player.class);
    when(client.getLocalPlayer()).thenReturn(player);
    when(client.isInInstancedRegion()).thenReturn(false);

    when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 9000, 0));
    assertTrue("a deep cave reads as underground", policy.isUnderground());

    when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
    assertFalse("an overworld tile reads as surface", policy.isUnderground());
  }

  private Object[] shouldEchoCases() {
    return new Object[] {
      new Object[] {true, 9000, true},
      new Object[] {false, 9000, false},
      new Object[] {true, 3200, false},
    };
  }

  @Test
  @Parameters(method = "shouldEchoCases")
  public void shouldEchoGatesOnToggleAndLocation(
      boolean caveEchoEnabled, int worldY, boolean expected) {
    Client client = mock(Client.class);
    VoicedDialogueConfig config = mock(VoicedDialogueConfig.class);
    Player player = mock(Player.class);
    when(client.getLocalPlayer()).thenReturn(player);
    when(client.isInInstancedRegion()).thenReturn(false);
    when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, worldY, 0));
    when(config.cloudCaveEcho()).thenReturn(caveEchoEnabled);

    assertEquals(expected, new CaveEchoPolicy(client, config).shouldEcho());
  }
}
