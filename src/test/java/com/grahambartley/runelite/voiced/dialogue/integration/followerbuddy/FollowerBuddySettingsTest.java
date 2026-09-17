package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;

public class FollowerBuddySettingsTest {

  private final ConfigManager configManager = mock(ConfigManager.class);
  private final FollowerBuddySettings settings = new FollowerBuddySettings(configManager);

  @Test
  public void theConfiguredNameIsReadOnceAndThenMemoised() {
    when(configManager.getConfiguration(
            FollowerBuddySettings.GROUP, FollowerBuddySettings.NAME_KEY))
        .thenReturn("Barkley");

    assertEquals("Barkley", settings.followerName());
    assertEquals("Barkley", settings.followerName());

    verify(configManager, times(1))
        .getConfiguration(FollowerBuddySettings.GROUP, FollowerBuddySettings.NAME_KEY);
  }

  @Test
  public void invalidatingPicksUpARename() {
    when(configManager.getConfiguration(
            FollowerBuddySettings.GROUP, FollowerBuddySettings.NAME_KEY))
        .thenReturn("Barkley", "Rufus");

    assertEquals("Barkley", settings.followerName());
    settings.invalidate();
    assertEquals("Rufus", settings.followerName());
  }

  @Test
  public void anAbsentOrBlankNameMeansThereIsNoCompanionToVoice() {
    when(configManager.getConfiguration(
            FollowerBuddySettings.GROUP, FollowerBuddySettings.NAME_KEY))
        .thenReturn(null);
    assertNull(
        "an absent group is a profile Follower Buddy never ran in, not a companion called"
            + " Follower",
        settings.followerName());

    settings.invalidate();
    when(configManager.getConfiguration(
            FollowerBuddySettings.GROUP, FollowerBuddySettings.NAME_KEY))
        .thenReturn("   ");
    assertNull(settings.followerName());
  }

  @Test
  public void theNameIsTrimmedBeforeItIsMatched() {
    when(configManager.getConfiguration(
            FollowerBuddySettings.GROUP, FollowerBuddySettings.NAME_KEY))
        .thenReturn("  Barkley  ");

    assertEquals("Barkley", settings.followerName());
  }

  @Test
  public void theOutfitGenderIsReadOnceAndThenMemoised() {
    when(configManager.getConfiguration(
            FollowerBuddySettings.GROUP, FollowerBuddySettings.OUTFIT_KEY))
        .thenReturn("HAIR=kit:128,gender=female");

    assertEquals(NpcGender.FEMALE, settings.outfitGender());
    assertEquals(NpcGender.FEMALE, settings.outfitGender());

    verify(configManager, times(1))
        .getConfiguration(FollowerBuddySettings.GROUP, FollowerBuddySettings.OUTFIT_KEY);
  }

  @Test
  public void invalidatingPicksUpARedressedFollower() {
    when(configManager.getConfiguration(
            FollowerBuddySettings.GROUP, FollowerBuddySettings.OUTFIT_KEY))
        .thenReturn("gender=male", "gender=female");

    assertEquals(NpcGender.MALE, settings.outfitGender());
    settings.invalidate();
    assertEquals(NpcGender.FEMALE, settings.outfitGender());
  }

  @Test
  public void mirroringIsReadLiveAndDefaultsToOffWhenAbsent() {
    when(configManager.getConfiguration(
            FollowerBuddySettings.GROUP, FollowerBuddySettings.MIRROR_TO_CHAT_KEY, Boolean.class))
        .thenReturn(null, Boolean.FALSE, Boolean.TRUE);

    assertFalse("an absent key is not a mirroring follower", settings.mirrorToChat());
    assertFalse(settings.mirrorToChat());
    assertTrue(settings.mirrorToChat());
  }

  @Test
  public void installationIsReadFromTheHubPluginList() {
    when(configManager.getConfiguration("runelite", "externalPlugins"))
        .thenReturn("voiced-dialogue,follower-buddy", "voiced-dialogue");

    assertTrue(settings.installedFromHub());
    assertFalse(
        "uninstalling drops it from the list even though its keys linger",
        settings.installedFromHub());
  }

  @Test
  public void onlyFollowerBuddysOwnGroupIsOwned() {
    assertTrue(FollowerBuddySettings.owns("followerbuddy"));
    assertFalse(FollowerBuddySettings.owns("voicedDialogue"));
    assertFalse(FollowerBuddySettings.owns(null));
  }
}
