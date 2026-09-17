package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

public final class FollowerBuddyPresence {

  static final String HUB_PLUGIN_ID = "follower-buddy";

  private FollowerBuddyPresence() {}

  public static boolean installedFromHub(String externalPlugins) {
    if (externalPlugins == null || externalPlugins.isEmpty()) {
      return false;
    }
    for (String entry : externalPlugins.split(",")) {
      if (HUB_PLUGIN_ID.equals(entry.trim())) {
        return true;
      }
    }
    return false;
  }

  public static boolean shouldWarnMirrorOff(boolean installedFromHub, boolean mirrorToChat) {
    return installedFromHub && !mirrorToChat;
  }
}
