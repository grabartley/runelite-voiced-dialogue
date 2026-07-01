package com.grahambartley.dialogue;

import com.grahambartley.VoicedDialogueConfig;
import com.grahambartley.synthesis.OpenRouterTtsBackend;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

/**
 * Posts the plugin's user-facing chat notices: the once-ever first-run onboarding guide, the
 * once-per-session missing-cloud-key warning, and one-off backend notices surfaced from a backend
 * thread. A fresh instance is created on each start-up, so the per-session guards reset on a
 * stop/start exactly as before; onboarding additionally persists across sessions via {@link
 * #ONBOARDING_SEEN_KEY}.
 */
@Slf4j
public final class ChatNoticeManager {

  /**
   * Hidden persisted flag marking that the first-run onboarding guide has been shown, so it appears
   * exactly once ever rather than every login. Read and set directly through {@link ConfigManager}.
   */
  static final String ONBOARDING_SEEN_KEY = "onboardingSeen";

  /** Chat-markup hex colour for plugin notices, so they stand out red from ordinary game chat. */
  private static final String CHAT_NOTICE_COLOR = "ff3333";

  private static final String ONBOARDING_MESSAGE =
      "Voiced Dialogue is on. It needs a free OpenRouter API key to voice dialogue: get one at"
          + " openrouter.ai and paste it into the plugin's Cloud Voice settings. Your dialogue text"
          + " is then sent to OpenRouter to be voiced. Until a key is set, lines stay silent.";

  private final Client client;
  private final ConfigManager configManager;
  private final ClientThread clientThread;
  private final VoicedDialogueConfig config;

  private boolean onboardingChecked;
  private boolean cloudKeyNoticeChecked;

  public ChatNoticeManager(
      Client client,
      ConfigManager configManager,
      ClientThread clientThread,
      VoicedDialogueConfig config) {
    this.client = client;
    this.configManager = configManager;
    this.clientThread = clientThread;
    this.config = config;
  }

  /**
   * Surfaces a one-time cloud-backend notice (e.g. "add an OpenRouter API key") to the player.
   * Fired from a backend thread, so the chat write is hopped onto the client thread.
   */
  public void notifyFromBackendThread(String message) {
    log.warn(message);
    clientThread.invokeLater(() -> addGameMessage(message));
  }

  /**
   * Shows the first-run onboarding guide exactly once, gated by the persisted {@link
   * #ONBOARDING_SEEN_KEY} flag and a per-session guard. Must be called on the game thread.
   */
  public void maybeShowOnboarding() {
    if (onboardingChecked) {
      return;
    }
    onboardingChecked = true;
    Boolean seen =
        configManager.getConfiguration(
            VoicedDialogueConfig.GROUP, ONBOARDING_SEEN_KEY, Boolean.class);
    if (!shouldShowOnboarding(seen)) {
      return;
    }
    addGameMessage(ONBOARDING_MESSAGE);
    configManager.setConfiguration(VoicedDialogueConfig.GROUP, ONBOARDING_SEEN_KEY, true);
  }

  /**
   * Pure decision for {@link #maybeShowOnboarding}: show the guide unless the persisted seen flag
   * is already true. A {@code null} flag (never set) means a fresh install, so the guide shows.
   */
  static boolean shouldShowOnboarding(Boolean seenFlag) {
    return !Boolean.TRUE.equals(seenFlag);
  }

  /**
   * Posts the missing-cloud-key notice once per session when no key is set, so a player who never
   * set a key is told their voice is effectively off. Must be called on the game thread. {@code
   * keyAvailable} is the backend's availability.
   */
  public void maybeWarnMissingCloudKey(boolean keyAvailable) {
    if (cloudKeyNoticeChecked) {
      return;
    }
    cloudKeyNoticeChecked = true;
    if (shouldWarnMissingCloudKey(keyAvailable)) {
      addGameMessage(OpenRouterTtsBackend.NO_KEY_NOTICE);
    }
  }

  /** Pure decision for {@link #maybeWarnMissingCloudKey}: warn only when the key is unavailable. */
  static boolean shouldWarnMissingCloudKey(boolean keyAvailable) {
    return !keyAvailable;
  }

  /**
   * Posts a single red, plugin-tagged notice into the game chat box. Red marks it as a plugin
   * notice that stands out from ordinary dialogue and game spam. Must be called on the client
   * thread.
   */
  private void addGameMessage(String message) {
    String line = "<col=" + CHAT_NOTICE_COLOR + ">[Voiced Dialogue] " + message + "</col>";
    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", line, null);
  }
}
