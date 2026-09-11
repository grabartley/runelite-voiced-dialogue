package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisBackend;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;

@Slf4j
public final class ChatNoticeManager {

  static final String ONBOARDING_SEEN_KEY = "onboardingSeen";

  private static final String CHAT_NOTICE_COLOR = "ff3333";

  private static final String ONBOARDING_MESSAGE =
      "Voiced Dialogue is on. It needs an API key to voice dialogue. Google AI Studio starts lines"
          + " almost instantly but begins at 100 fresh lines a day, prefetched options included,"
          + " and billing does not lift that: set Voice Provider to Google AI Studio and use a key"
          + " from aistudio.google.com with billing enabled. OpenRouter is"
          + " simpler to set up and has no daily cap, at the cost of much slower lines: use a key"
          + " from openrouter.ai instead. Your dialogue text is sent to whichever provider you"
          + " pick. Until a key is set, lines stay silent.";

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

  public void notifyFromBackendThread(String message) {
    log.warn(message);
    clientThread.invokeLater(() -> addGameMessage(message));
  }

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

  static boolean shouldShowOnboarding(Boolean seenFlag) {
    return !Boolean.TRUE.equals(seenFlag);
  }

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

  private void addGameMessage(String message) {
    String line = "<col=" + CHAT_NOTICE_COLOR + ">[Voiced Dialogue] " + message + "</col>";
    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", line, null);
  }
}
