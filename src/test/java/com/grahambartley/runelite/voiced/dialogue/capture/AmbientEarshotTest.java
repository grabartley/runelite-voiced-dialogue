package com.grahambartley.runelite.voiced.dialogue.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AmbientEarshotTest {

  @Test
  public void earshotStopsWhereTheClientStopsRenderingNpcs() {
    assertEquals(16, AmbientEarshot.EARSHOT_TILES);
  }

  @Test
  public void aSpeakerInsideTheRenderedAreaIsWithinEarshot() {
    assertTrue(AmbientEarshot.isWithinEarshot(0));
    assertTrue(AmbientEarshot.isWithinEarshot(AmbientEarshot.EARSHOT_TILES));
  }

  @Test
  public void aSpeakerPastTheRenderedAreaIsOutOfEarshot() {
    assertFalse(AmbientEarshot.isWithinEarshot(AmbientEarshot.EARSHOT_TILES + 1));
  }

  @Test
  public void anUnknownDistanceIsOutOfEarshot() {
    assertFalse(AmbientEarshot.isWithinEarshot(Integer.MAX_VALUE));
    assertFalse(AmbientEarshot.isWithinEarshot(-1));
  }

  @Test
  public void anNpcStandingOnYouIsAtTheConfiguredVolume() {
    assertEquals(60, AmbientEarshot.volumeAt(60, 0));
  }

  @Test
  public void anNpcAtTheEdgeOfEarshotIsAsFaintAsItGets() {
    assertEquals(
        AmbientEarshot.FAINTEST, AmbientEarshot.volumeAt(60, AmbientEarshot.EARSHOT_TILES));
  }

  @Test
  public void halfwayOutIsAboutHalfTheConfiguredVolume() {
    assertEquals(31, AmbientEarshot.volumeAt(60, 8));
  }

  @Test
  public void volumeFallsOffWithEveryTileWalked() {
    int previous = Integer.MAX_VALUE;
    for (int tiles = 0; tiles <= AmbientEarshot.EARSHOT_TILES; tiles++) {
      int current = AmbientEarshot.volumeAt(100, tiles);
      assertTrue("volume must never rise as distance grows", current <= previous);
      previous = current;
    }
  }

  @Test
  public void aMutedPlayerStaysMutedAtEveryDistance() {
    assertEquals(0, AmbientEarshot.volumeAt(0, 0));
    assertEquals(0, AmbientEarshot.volumeAt(0, AmbientEarshot.EARSHOT_TILES));
  }
}
