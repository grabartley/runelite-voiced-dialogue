package com.grahambartley.runelite.voiced.dialogue.capture;

public final class AmbientEarshot {

  public static final int EARSHOT_TILES = 16;

  static final int FAINTEST = 1;

  private AmbientEarshot() {}

  public static boolean isWithinEarshot(int distanceTiles) {
    return distanceTiles >= 0 && distanceTiles <= EARSHOT_TILES;
  }

  public static int volumeAt(int configuredVolume, int distanceTiles) {
    if (configuredVolume <= FAINTEST) {
      return configuredVolume;
    }
    if (distanceTiles >= EARSHOT_TILES) {
      return FAINTEST;
    }
    int travelled = Math.max(0, distanceTiles);
    int span = configuredVolume - FAINTEST;
    return configuredVolume - (span * travelled) / EARSHOT_TILES;
  }
}
