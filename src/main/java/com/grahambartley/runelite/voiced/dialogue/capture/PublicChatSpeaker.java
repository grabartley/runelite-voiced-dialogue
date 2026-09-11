package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.function.BooleanSupplier;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;

public final class PublicChatSpeaker {

  private final Client client;
  private final DialogueTextCleaner textCleaner;
  private final SynthesisDispatcher dispatcher;

  private final BooleanSupplier enabled;

  public PublicChatSpeaker(
      Client client,
      DialogueTextCleaner textCleaner,
      SynthesisDispatcher dispatcher,
      BooleanSupplier enabled) {
    this.client = client;
    this.textCleaner = textCleaner;
    this.dispatcher = dispatcher;
    this.enabled = enabled;
  }

  public void onChatMessage(ChatMessage event) {
    if (event.getType() != ChatMessageType.PUBLICCHAT || !enabled.getAsBoolean()) {
      return;
    }
    Player local = client.getLocalPlayer();
    if (local == null || !PublicChatPolicy.isSelfPublicChat(event.getName(), local.getName())) {
      return;
    }
    String cleaned = textCleaner.clean(event.getMessage());
    if (cleaned.isEmpty()) {
      return;
    }
    dispatcher.speakPublicChat(cleaned);
  }
}
