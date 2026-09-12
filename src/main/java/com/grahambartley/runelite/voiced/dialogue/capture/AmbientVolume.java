package com.grahambartley.runelite.voiced.dialogue.capture;

public final class AmbientVolume {

  static final int FAINTEST = 1;

  private AmbientVolume() {}

  public static int atDistance(int configuredVolume, int distanceTiles, int rangeTiles) {
    if (configuredVolume <= FAINTEST || rangeTiles <= 0) {
      return configuredVolume;
    }
    if (distanceTiles >= rangeTiles) {
      return FAINTEST;
    }
    int travelled = Math.max(0, distanceTiles);
    int span = configuredVolume - FAINTEST;
    return configuredVolume - (span * travelled) / rangeTiles;
  }
}
