package com.grahambartley.runelite.voiced.dialogue.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AmbientVolumeTest {

  @Test
  public void anNpcStandingOnYouIsAtTheConfiguredVolume() {
    assertEquals(60, AmbientVolume.atDistance(60, 0, 20));
  }

  @Test
  public void anNpcAtTheEdgeOfRangeIsAsFaintAsItGets() {
    assertEquals(AmbientVolume.FAINTEST, AmbientVolume.atDistance(60, 20, 20));
  }

  @Test
  public void anNpcBeyondRangeNeverGoesQuieterThanTheFaintestStep() {
    assertEquals(AmbientVolume.FAINTEST, AmbientVolume.atDistance(60, 500, 20));
  }

  @Test
  public void halfwayOutIsAboutHalfTheConfiguredVolume() {
    assertEquals(31, AmbientVolume.atDistance(60, 10, 20));
  }

  @Test
  public void volumeFallsOffWithEveryTileWalked() {
    int previous = Integer.MAX_VALUE;
    for (int tiles = 0; tiles <= 20; tiles++) {
      int current = AmbientVolume.atDistance(100, tiles, 20);
      assertTrue("volume must never rise as distance grows", current <= previous);
      previous = current;
    }
  }

  @Test
  public void aMutedPlayerStaysMutedAtEveryDistance() {
    assertEquals(0, AmbientVolume.atDistance(0, 0, 20));
    assertEquals(0, AmbientVolume.atDistance(0, 20, 20));
  }

  @Test
  public void aWiderRangeStretchesTheFalloffRatherThanSteepeningIt() {
    assertTrue(
        "the same tile is louder when the range is wider",
        AmbientVolume.atDistance(100, 10, 50) > AmbientVolume.atDistance(100, 10, 20));
  }

  @Test
  public void aRangeOfNothingLeavesTheConfiguredVolumeAlone() {
    assertEquals(60, AmbientVolume.atDistance(60, 5, 0));
  }
}
