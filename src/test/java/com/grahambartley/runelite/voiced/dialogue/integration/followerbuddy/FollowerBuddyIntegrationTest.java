package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.capture.ChatNoticeManager;
import com.grahambartley.runelite.voiced.dialogue.capture.DialogueTextCleaner;
import com.grahambartley.runelite.voiced.dialogue.profile.ProfanityFilter;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import org.junit.Before;
import org.junit.Test;

public class FollowerBuddyIntegrationTest {

  private static final String FOLLOWER_NAME = "Barkley";

  private final ConfigManager configManager = mock(ConfigManager.class);
  private final ChatNoticeManager notices = mock(ChatNoticeManager.class);
  private final SynthesisDispatcher dispatcher = mock(SynthesisDispatcher.class);
  private final VoicedDialogueConfig config = mock(VoicedDialogueConfig.class);

  private FollowerBuddyIntegration integration;

  @Before
  public void setUp() {
    when(config.voiceFollower()).thenReturn(true);
    when(config.followerVoice()).thenReturn(FollowerVoice.AUTO);
    when(configManager.getConfiguration(
            FollowerBuddySettings.GROUP, FollowerBuddySettings.NAME_KEY))
        .thenReturn(FOLLOWER_NAME);
    integration =
        new FollowerBuddyIntegration(
            configManager,
            notices,
            new DialogueTextCleaner(new ProfanityFilter()),
            dispatcher,
            config);
  }

  @Test
  public void aMirroredFollowerLineIsVoiced() {
    installedFromHub("voiced-dialogue,follower-buddy");

    integration.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "Woof!"));

    verify(dispatcher).speakFollower("Woof!", NpcGender.MALE);
  }

  @Test
  public void theOutfitSeedsTheVoiceGenderWhenTheSettingIsAuto() {
    when(configManager.getConfiguration(
            FollowerBuddySettings.GROUP, FollowerBuddySettings.OUTFIT_KEY))
        .thenReturn("HAIR=kit:128,JAW=kit:296,gender=female");

    integration.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "Woof!"));

    verify(dispatcher).speakFollower("Woof!", NpcGender.FEMALE);
  }

  @Test
  public void theSettingOverridesTheOutfit() {
    when(config.followerVoice()).thenReturn(FollowerVoice.TYPE_A);
    when(configManager.getConfiguration(
            FollowerBuddySettings.GROUP, FollowerBuddySettings.OUTFIT_KEY))
        .thenReturn("gender=female");

    integration.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "Woof!"));

    verify(dispatcher).speakFollower("Woof!", NpcGender.MALE);
  }

  @Test
  public void withTheFeatureOffNothingIsVoicedAndNoCrossPluginReadHappens() {
    when(config.voiceFollower()).thenReturn(false);

    integration.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "Woof!"));
    integration.onGameTick();

    verify(dispatcher, never()).speakFollower(anyString(), any());
    verifyNoInteractions(configManager);
    verifyNoInteractions(notices);
  }

  @Test
  public void anInstalledFollowerBuddyWithMirroringOffEarnsOneNotice() {
    installedFromHub("follower-buddy");
    mirrorToChat(false);

    integration.onGameTick();
    integration.onGameTick();
    integration.onGameTick();

    verify(notices, times(1)).postNotice(FollowerBuddyIntegration.MIRROR_OFF_NOTICE);
  }

  @Test
  public void anInstalledFollowerBuddyThatMirrorsEarnsNoNotice() {
    installedFromHub("follower-buddy");
    mirrorToChat(true);

    integration.onGameTick();

    verify(notices, never()).postNotice(anyString());
  }

  @Test
  public void staleConfigFromARemovedFollowerBuddyEarnsNoNotice() {
    installedFromHub("voiced-dialogue");
    mirrorToChat(false);

    integration.onGameTick();

    verify(notices, never()).postNotice(anyString());
  }

  @Test
  public void withFollowerBuddyNeverInstalledNoNoticeIsPosted() {
    installedFromHub(null);

    integration.onGameTick();

    verify(notices, never()).postNotice(anyString());
  }

  @Test
  public void switchingOurToggleOnReArmsTheNotice() {
    installedFromHub("follower-buddy");
    mirrorToChat(false);

    integration.onGameTick();
    integration.onConfigChanged(
        configChange(VoicedDialogueConfig.GROUP, VoicedDialogueConfig.VOICE_FOLLOWER_KEY));
    integration.onGameTick();

    verify(notices, times(2)).postNotice(FollowerBuddyIntegration.MIRROR_OFF_NOTICE);
  }

  @Test
  public void anUnrelatedSettingOfOursDoesNotReArmTheNotice() {
    installedFromHub("follower-buddy");
    mirrorToChat(false);

    integration.onGameTick();
    integration.onConfigChanged(configChange(VoicedDialogueConfig.GROUP, "volume"));
    integration.onGameTick();

    verify(notices, times(1)).postNotice(FollowerBuddyIntegration.MIRROR_OFF_NOTICE);
  }

  @Test
  public void renamingTheFollowerIsPickedUpWithoutARestart() {
    integration.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "Woof!"));

    when(configManager.getConfiguration(
            FollowerBuddySettings.GROUP, FollowerBuddySettings.NAME_KEY))
        .thenReturn("Rufus");
    integration.onConfigChanged(
        configChange(FollowerBuddySettings.GROUP, FollowerBuddySettings.NAME_KEY));

    integration.onChatMessage(chat(ChatMessageType.PUBLICCHAT, "Rufus", "Woof again!"));

    verify(dispatcher).speakFollower("Woof!", NpcGender.MALE);
    verify(dispatcher).speakFollower("Woof again!", NpcGender.MALE);
  }

  @Test
  public void redressingTheFollowerIsPickedUpWithoutARestart() {
    when(configManager.getConfiguration(
            FollowerBuddySettings.GROUP, FollowerBuddySettings.OUTFIT_KEY))
        .thenReturn("gender=male", "gender=female");

    integration.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "Woof!"));
    integration.onConfigChanged(
        configChange(FollowerBuddySettings.GROUP, FollowerBuddySettings.OUTFIT_KEY));
    integration.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "Woof again!"));

    verify(dispatcher).speakFollower("Woof!", NpcGender.MALE);
    verify(dispatcher).speakFollower("Woof again!", NpcGender.FEMALE);
  }

  @Test
  public void switchingFollowerBuddysOwnMirroringOffReArmsTheNotice() {
    installedFromHub("follower-buddy");
    mirrorToChat(false);

    integration.onGameTick();
    integration.onConfigChanged(
        configChange(FollowerBuddySettings.GROUP, FollowerBuddySettings.MIRROR_TO_CHAT_KEY));
    integration.onGameTick();

    verify(notices, times(2)).postNotice(FollowerBuddyIntegration.MIRROR_OFF_NOTICE);
  }

  @Test
  public void anUnrelatedFollowerBuddyChangeDoesNotReArmTheNotice() {
    installedFromHub("follower-buddy");
    mirrorToChat(false);

    integration.onGameTick();
    integration.onConfigChanged(
        configChange(FollowerBuddySettings.GROUP, FollowerBuddySettings.OUTFIT_KEY));
    integration.onGameTick();

    verify(notices, times(1)).postNotice(FollowerBuddyIntegration.MIRROR_OFF_NOTICE);
  }

  @Test
  public void withFollowerBuddyNeverInstalledNoPlayersChatIsEverVoiced() {
    when(configManager.getConfiguration(
            FollowerBuddySettings.GROUP, FollowerBuddySettings.NAME_KEY))
        .thenReturn(null);

    integration.onChatMessage(chat(ChatMessageType.PUBLICCHAT, "Follower", "Hi there!"));
    integration.onChatMessage(chat(ChatMessageType.PUBLICCHAT, "Zezima", "Hi there!"));

    verify(dispatcher, never()).speakFollower(anyString(), any());
  }

  private void installedFromHub(String externalPlugins) {
    when(configManager.getConfiguration("runelite", "externalPlugins")).thenReturn(externalPlugins);
  }

  private void mirrorToChat(boolean mirrored) {
    when(configManager.getConfiguration(
            eq(FollowerBuddySettings.GROUP),
            eq(FollowerBuddySettings.MIRROR_TO_CHAT_KEY),
            eq(Boolean.class)))
        .thenReturn(mirrored);
  }

  private static ConfigChanged configChange(String group, String key) {
    ConfigChanged event = new ConfigChanged();
    event.setGroup(group);
    event.setKey(key);
    return event;
  }

  private static ChatMessage chat(ChatMessageType type, String name, String message) {
    ChatMessage event = new ChatMessage();
    event.setType(type);
    event.setName(name);
    event.setMessage(message);
    return event;
  }
}
