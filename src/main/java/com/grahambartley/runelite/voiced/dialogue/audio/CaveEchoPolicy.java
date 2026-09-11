package com.grahambartley.runelite.voiced.dialogue.audio;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

@Slf4j
public final class CaveEchoPolicy {

  static final Set<Integer> POH_REGION_IDS =
      Set.of(7257, 7534, 7535, 7790, 7791, 8046, 8047, 8302, 8303);

  private final Client client;
  private final VoicedDialogueConfig config;

  public CaveEchoPolicy(Client client, VoicedDialogueConfig config) {
    this.client = client;
    this.config = config;
  }

  public boolean shouldEcho() {
    return config.cloudCaveEcho() && isUnderground();
  }

  boolean isUnderground() {
    Player local = client.getLocalPlayer();
    if (local == null) {
      return false;
    }
    LocalPoint lp = local.getLocalLocation();
    WorldPoint wp =
        client.isInInstancedRegion() && lp != null
            ? WorldPoint.fromLocalInstance(client, lp)
            : local.getWorldLocation();
    if (wp == null) {
      return false;
    }
    boolean underground = isUndergroundPoint(wp);
    if (config.debugMode()) {
      log.info(
          "[TTS echo] x={} y={} plane={} region={} instanced={} underground={}",
          wp.getX(),
          wp.getY(),
          wp.getPlane(),
          wp.getRegionID(),
          client.isInInstancedRegion(),
          underground);
    }
    return underground;
  }

  static boolean isUndergroundPoint(WorldPoint wp) {
    if (POH_REGION_IDS.contains(wp.getRegionID())) {
      return false;
    }
    return WorldPoint.getMirrorPoint(wp, true).getY() >= Constants.OVERWORLD_MAX_Y;
  }
}
