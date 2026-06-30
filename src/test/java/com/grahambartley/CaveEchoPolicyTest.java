package com.grahambartley;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.grahambartley.TTSDialogueConfig.VoiceBackend;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/**
 * Covers the cave-echo seams: {@link CaveEchoPolicy#isUndergroundPoint(WorldPoint)}, the coordinate
 * predicate behind underground detection; {@link CaveEchoPolicy#shouldEchoLine}, the pure gate; and
 * {@link CaveEchoPolicy#isUnderground()}, the client read that feeds the gate.
 */
public class CaveEchoPolicyTest {

  @Test
  public void justBelowTheOverworldCeilingIsSurface() {
    assertFalse(
        CaveEchoPolicy.isUndergroundPoint(new WorldPoint(3200, Constants.OVERWORLD_MAX_Y - 1, 0)));
  }

  @Test
  public void atAndAboveTheOverworldCeilingIsUnderground() {
    assertTrue(
        CaveEchoPolicy.isUndergroundPoint(new WorldPoint(3200, Constants.OVERWORLD_MAX_Y, 0)));
    assertTrue(
        "a deep cave reads as underground",
        CaveEchoPolicy.isUndergroundPoint(new WorldPoint(3200, 9000, 0)));
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
  public void echoesOnlyForCloudToggleOnUnderground() {
    assertTrue(CaveEchoPolicy.shouldEchoLine(VoiceBackend.CLOUD, true, true));
  }

  @Test
  public void noEchoOnLocalBackend() {
    assertFalse(CaveEchoPolicy.shouldEchoLine(VoiceBackend.LOCAL, true, true));
  }

  @Test
  public void noEchoWhenToggleOff() {
    assertFalse(CaveEchoPolicy.shouldEchoLine(VoiceBackend.CLOUD, false, true));
  }

  @Test
  public void noEchoAboveGround() {
    assertFalse(CaveEchoPolicy.shouldEchoLine(VoiceBackend.CLOUD, true, false));
  }

  @Test
  public void isUndergroundReadsTheLivePlayerLocation() {
    Client client = mock(Client.class);
    TTSDialogueConfig config = mock(TTSDialogueConfig.class);
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

  @Test
  public void shouldEchoCombinesBackendToggleAndLocation() {
    Client client = mock(Client.class);
    TTSDialogueConfig config = mock(TTSDialogueConfig.class);
    Player player = mock(Player.class);
    when(client.getLocalPlayer()).thenReturn(player);
    when(client.isInInstancedRegion()).thenReturn(false);
    when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 9000, 0));
    when(config.voiceBackend()).thenReturn(VoiceBackend.CLOUD);
    when(config.cloudCaveEcho()).thenReturn(true);

    assertTrue(new CaveEchoPolicy(client, config).shouldEcho());
  }
}
