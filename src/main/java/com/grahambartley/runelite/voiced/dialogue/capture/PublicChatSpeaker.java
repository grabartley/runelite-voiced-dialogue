package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.function.BooleanSupplier;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;

/**
 * Voices the local player's own public chat (default off). Only the local player's {@code
 * PUBLICCHAT} stream is spoken; other players' public messages, and every other chat type, are
 * ignored. The message is cleaned with the same {@link DialogueTextCleaner} as dialogue and voiced
 * through the player path with translation bypassed. Reads the client only on the game thread.
 */
public final class PublicChatSpeaker {

  private final Client client;
  private final DialogueTextCleaner textCleaner;
  private final SynthesisDispatcher dispatcher;

  /** The feature toggle, read live so flipping it takes effect immediately. */
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
    if (!enabled.getAsBoolean() || event.getType() != ChatMessageType.PUBLICCHAT) {
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
