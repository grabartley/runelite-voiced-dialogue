package com.grahambartley.runelite.voiced.dialogue.dialogue;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.synthesis.OpenRouterTtsBackend;
import com.grahambartley.runelite.voiced.dialogue.synthesis.SynthesisBackend;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The plugin's user-facing chat notices: the once-ever first-run onboarding guide and the
 * once-per-session missing-cloud-key warning, plus the pure decisions behind them.
 */
@RunWith(JUnitParamsRunner.class)
public class ChatNoticeManagerTest {

  private final Client client = mock(Client.class);
  private final ConfigManager configManager = mock(ConfigManager.class);
  private final ClientThread clientThread = mock(ClientThread.class);
  private final VoicedDialogueConfig config = mock(VoicedDialogueConfig.class);
  private final ChatNoticeManager manager =
      new ChatNoticeManager(client, configManager, clientThread, config);

  private Object[] onboardingCases() {
    return new Object[] {
      // fresh install (flag never set) shows the guide
      new Object[] {null, true},
      // flag explicitly false shows the guide
      new Object[] {false, true},
      // flag true suppresses the guide
      new Object[] {true, false},
    };
  }

  @Test
  @Parameters(method = "onboardingCases")
  public void shouldShowOnboardingOnlyUntilSeen(Boolean seen, boolean expected) {
    assertEquals(expected, ChatNoticeManager.shouldShowOnboarding(seen));
  }

  private Object[] missingCloudKeyCases() {
    return new Object[] {
      // a blank/missing key warns
      new Object[] {false, true},
      // a key set stays quiet
      new Object[] {true, false},
    };
  }

  @Test
  @Parameters(method = "missingCloudKeyCases")
  public void shouldWarnMissingCloudKeyOnlyWhenNoKey(boolean keySet, boolean expected) {
    assertEquals(expected, ChatNoticeManager.shouldWarnMissingCloudKey(keySet));
  }

  @Test
  public void onboardingPostsOnceOnFreshInstallAndPersistsTheFlag() {
    when(configManager.getConfiguration("voicedDialogue", "onboardingSeen", Boolean.class))
        .thenReturn(null);

    manager.maybeShowOnboarding();
    manager.maybeShowOnboarding();

    verify(client, times(1))
        .addChatMessage(
            eq(ChatMessageType.GAMEMESSAGE), eq(""), contains("Voiced Dialogue is on"), isNull());
    verify(configManager, times(1)).setConfiguration("voicedDialogue", "onboardingSeen", true);
  }

  @Test
  public void onboardingStaysQuietOnceSeen() {
    when(configManager.getConfiguration("voicedDialogue", "onboardingSeen", Boolean.class))
        .thenReturn(true);

    manager.maybeShowOnboarding();

    verify(client, never())
        .addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""), contains(""), isNull());
    verify(configManager, never()).setConfiguration("voicedDialogue", "onboardingSeen", true);
  }

  @Test
  public void missingKeyWarningPostsOnceWithTheBackendsOwnNotice() {
    manager.maybeWarnMissingCloudKey(backend(false, OpenRouterTtsBackend.NO_KEY_NOTICE));
    manager.maybeWarnMissingCloudKey(backend(false, OpenRouterTtsBackend.NO_KEY_NOTICE));

    verify(client, times(1))
        .addChatMessage(
            eq(ChatMessageType.GAMEMESSAGE),
            eq(""),
            contains(OpenRouterTtsBackend.NO_KEY_NOTICE),
            isNull());
  }

  @Test
  public void missingKeyWarningNamesTheActiveProvider() {
    manager.maybeWarnMissingCloudKey(backend(false, "Add your Google AI Studio API key"));

    verify(client, times(1))
        .addChatMessage(
            eq(ChatMessageType.GAMEMESSAGE),
            eq(""),
            contains("Add your Google AI Studio API key"),
            isNull());
  }

  @Test
  public void missingKeyWarningStaysQuietWhenKeyAvailable() {
    manager.maybeWarnMissingCloudKey(backend(true, OpenRouterTtsBackend.NO_KEY_NOTICE));

    verify(client, never())
        .addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""), contains(""), isNull());
  }

  @Test
  public void clearingTheKeyLaterInTheSessionStillWarnsOnce() {
    // The player starts with a working key, then clears it mid-session: they should be told why
    // dialogue went silent, and told only once.
    manager.maybeWarnMissingCloudKey(backend(true, OpenRouterTtsBackend.NO_KEY_NOTICE));
    manager.maybeWarnMissingCloudKey(backend(false, OpenRouterTtsBackend.NO_KEY_NOTICE));
    manager.maybeWarnMissingCloudKey(backend(false, OpenRouterTtsBackend.NO_KEY_NOTICE));

    verify(client, times(1))
        .addChatMessage(
            eq(ChatMessageType.GAMEMESSAGE),
            eq(""),
            contains(OpenRouterTtsBackend.NO_KEY_NOTICE),
            isNull());
  }

  private static SynthesisBackend backend(boolean available, String missingKeyNotice) {
    SynthesisBackend backend = mock(SynthesisBackend.class);
    when(backend.isAvailable()).thenReturn(available);
    when(backend.missingKeyNotice()).thenReturn(missingKeyNotice);
    return backend;
  }
}
