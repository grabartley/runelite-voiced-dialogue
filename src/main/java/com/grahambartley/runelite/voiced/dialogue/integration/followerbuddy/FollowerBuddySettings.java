package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import net.runelite.client.config.ConfigManager;

public final class FollowerBuddySettings {

  static final String GROUP = "followerbuddy";

  static final String NAME_KEY = "followerName";

  static final String MIRROR_TO_CHAT_KEY = "mirrorToChat";

  static final String OUTFIT_KEY = "customOutfit";

  static final String DEFAULT_NAME = "Follower";

  private static final String RUNELITE_GROUP = "runelite";

  private static final String EXTERNAL_PLUGINS_KEY = "externalPlugins";

  private final ConfigManager configManager;

  private String cachedName;
  private NpcGender cachedOutfitGender;

  public FollowerBuddySettings(ConfigManager configManager) {
    this.configManager = configManager;
  }

  public static boolean owns(String configGroup) {
    return GROUP.equals(configGroup);
  }

  public void invalidate() {
    cachedName = null;
    cachedOutfitGender = null;
  }

  public String followerName() {
    String name = cachedName;
    if (name == null) {
      String stored = configManager.getConfiguration(GROUP, NAME_KEY);
      name = stored == null || stored.trim().isEmpty() ? DEFAULT_NAME : stored.trim();
      cachedName = name;
    }
    return name;
  }

  public NpcGender outfitGender() {
    NpcGender gender = cachedOutfitGender;
    if (gender == null) {
      gender = FollowerOutfit.genderOf(configManager.getConfiguration(GROUP, OUTFIT_KEY));
      cachedOutfitGender = gender;
    }
    return gender;
  }

  public boolean mirrorToChat() {
    Boolean mirrored = configManager.getConfiguration(GROUP, MIRROR_TO_CHAT_KEY, Boolean.class);
    return Boolean.TRUE.equals(mirrored);
  }

  public boolean installedFromHub() {
    return FollowerBuddyPresence.installedFromHub(
        configManager.getConfiguration(RUNELITE_GROUP, EXTERNAL_PLUGINS_KEY));
  }
}
