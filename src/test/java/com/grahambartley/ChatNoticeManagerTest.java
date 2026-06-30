package com.grahambartley;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.TTSDialogueConfig.VoiceBackend;
import com.grahambartley.synthesis.OpenRouterTtsBackend;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;

/**
 * The plugin's user-facing chat notices: the once-ever first-run onboarding guide and the
 * once-per-session missing-cloud-key warning, plus the pure decisions behind them.
 */
public class ChatNoticeManagerTest {

  private final Client client = mock(Client.class);
  private final ConfigManager configManager = mock(ConfigManager.class);
  private final ClientThread clientThread = mock(ClientThread.class);
  private final TTSDialogueConfig config = mock(TTSDialogueConfig.class);
  private final ChatNoticeManager manager =
      new ChatNoticeManager(client, configManager, clientThread, config);

  @Test
  public void shouldShowOnboardingOnlyUntilSeen() {
    assertTrue(
        "fresh install (flag never set) shows the guide",
        ChatNoticeManager.shouldShowOnboarding(null));
    assertTrue(
        "flag explicitly false shows the guide", ChatNoticeManager.shouldShowOnboarding(false));
    assertFalse("flag true suppresses the guide", ChatNoticeManager.shouldShowOnboarding(true));
  }

  @Test
  public void shouldWarnMissingCloudKeyOnlyForCloudWithNoKey() {
    assertTrue(
        "Cloud with a blank key warns",
        ChatNoticeManager.shouldWarnMissingCloudKey(VoiceBackend.CLOUD, false));
    assertFalse(
        "Cloud with a key set stays quiet",
        ChatNoticeManager.shouldWarnMissingCloudKey(VoiceBackend.CLOUD, true));
    assertFalse(
        "Local needs no key, so it never warns",
        ChatNoticeManager.shouldWarnMissingCloudKey(VoiceBackend.LOCAL, false));
    assertFalse(
        "Local with a key set still never warns",
        ChatNoticeManager.shouldWarnMissingCloudKey(VoiceBackend.LOCAL, true));
  }

  @Test
  public void onboardingPostsOnceOnFreshInstallAndPersistsTheFlag() {
    when(configManager.getConfiguration("ttsDialogue", "onboardingSeen", Boolean.class))
        .thenReturn(null);

    manager.maybeShowOnboarding();
    manager.maybeShowOnboarding();

    verify(client, times(1))
        .addChatMessage(
            eq(ChatMessageType.GAMEMESSAGE), eq(""), contains("Voiced Dialogue is on"), isNull());
    verify(configManager, times(1)).setConfiguration("ttsDialogue", "onboardingSeen", true);
  }

  @Test
  public void onboardingStaysQuietOnceSeen() {
    when(configManager.getConfiguration("ttsDialogue", "onboardingSeen", Boolean.class))
        .thenReturn(true);

    manager.maybeShowOnboarding();

    verify(client, never())
        .addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""), contains(""), isNull());
    verify(configManager, never()).setConfiguration("ttsDialogue", "onboardingSeen", true);
  }

  @Test
  public void missingKeyWarningPostsOnceForCloudWithNoKey() {
    when(config.voiceBackend()).thenReturn(VoiceBackend.CLOUD);

    manager.maybeWarnMissingCloudKey(false);
    manager.maybeWarnMissingCloudKey(false);

    verify(client, times(1))
        .addChatMessage(
            eq(ChatMessageType.GAMEMESSAGE),
            eq(""),
            contains(OpenRouterTtsBackend.NO_KEY_NOTICE),
            isNull());
  }

  @Test
  public void missingKeyWarningStaysQuietWhenKeyAvailable() {
    when(config.voiceBackend()).thenReturn(VoiceBackend.CLOUD);

    manager.maybeWarnMissingCloudKey(true);

    verify(client, never())
        .addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""), contains(""), isNull());
  }
}
