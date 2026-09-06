package com.grahambartley.runelite.voiced.dialogue.dialogue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.synthesis.SynthesisBackend;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;

/**
 * Posts the plugin's user-facing chat output: the once-ever first-run onboarding guide, the
 * once-per-session missing-cloud-key warning, one-off backend notices surfaced from a backend
 * thread, and the answer to the {@code ::voicedspend} command.
 *
 * <p>The two kinds are posted differently on purpose. A notice is an unprompted red warning the
 * player did not ask for, written straight into the chat box so it cannot be delayed. A command
 * response follows the client's convention for {@code ::} commands and goes through {@link
 * ChatMessageManager}, which also makes it postable from a background thread. A fresh instance is
 * created on each start-up, so the per-session guards reset on a stop/start; onboarding
 * additionally persists across sessions via {@link #ONBOARDING_SEEN_KEY}.
 */
@Slf4j
public final class ChatNoticeManager {

  /**
   * Hidden persisted flag marking that the first-run onboarding guide has been shown, so it appears
   * exactly once ever rather than every login. Read and set directly through {@link ConfigManager}.
   */
  static final String ONBOARDING_SEEN_KEY = "onboardingSeen";

  /** Red, so an unprompted plugin notice stands out from ordinary dialogue and game spam. */
  private static final String CHAT_NOTICE_COLOR = "ff3333";

  private static final String ONBOARDING_MESSAGE =
      "Voiced Dialogue is on. It needs an API key to voice dialogue. Google AI Studio is"
          + " recommended, since dialogue starts speaking almost instantly: set Voice Provider to"
          + " Google AI Studio and use a key from aistudio.google.com with billing enabled. For a"
          + " simpler setup at the cost of much slower lines, use an OpenRouter key from"
          + " openrouter.ai instead. Your dialogue text is sent to whichever provider you pick."
          + " Until a key is set, lines stay silent.";

  private final Client client;
  private final ConfigManager configManager;
  private final ClientThread clientThread;
  private final ChatMessageManager chatMessageManager;

  private boolean onboardingChecked;
  private boolean cloudKeyNoticeChecked;

  public ChatNoticeManager(
      Client client,
      ConfigManager configManager,
      ClientThread clientThread,
      ChatMessageManager chatMessageManager) {
    this.client = client;
    this.configManager = configManager;
    this.clientThread = clientThread;
    this.chatMessageManager = chatMessageManager;
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
   * Posts the active backend's missing-key notice once per session when no key is set, so a player
   * who never set a key for the selected provider is told their voice is effectively off. Must be
   * called on the game thread.
   *
   * <p>The one-shot guard is only consumed when the notice actually fires. Consuming it on the
   * first game tick regardless would mean a player who starts with a valid key and later clears it
   * is never told why dialogue went silent.
   */
  public void maybeWarnMissingCloudKey(SynthesisBackend backend) {
    if (cloudKeyNoticeChecked || !shouldWarnMissingCloudKey(backend.isAvailable())) {
      return;
    }
    cloudKeyNoticeChecked = true;
    addGameMessage(backend.missingKeyNotice());
  }

  static boolean shouldWarnMissingCloudKey(boolean keyAvailable) {
    return !keyAvailable;
  }

  /**
   * Answers a chat command the player typed (the {@code ::voicedspend} readout), following the
   * client's own convention for command responses: the line is handed to {@link ChatMessageManager}
   * as a {@link QueuedMessage} rather than written straight into the chat box, and coloured through
   * {@link ChatMessageBuilder} so it honours the player's configured chat colours instead of a
   * hard-coded one.
   *
   * <p>Safe to call from any thread, unlike the direct chat write the notices use: the manager's
   * queue is concurrent and is drained on the game tick, which is what lets the readout be posted
   * straight from the thread that read the provider balance. Carries no once-per-session guard and
   * writes no log line, since it is an answer the player asked for rather than a warning.
   */
  public void postCommandResponse(String message) {
    String formatted =
        new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT)
            .append("[Voiced Dialogue] ")
            .append(ChatColorType.NORMAL)
            .append(message)
            .build();
    chatMessageManager.queue(
        QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(formatted)
            .build());
  }

  /** Must be called on the client thread. */
  private void addGameMessage(String message) {
    String line = "<col=" + CHAT_NOTICE_COLOR + ">[Voiced Dialogue] " + message + "</col>";
    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", line, null);
  }
}
