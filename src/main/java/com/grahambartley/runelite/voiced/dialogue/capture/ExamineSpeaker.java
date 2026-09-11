package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;

public final class ExamineSpeaker {

  private static final Set<ChatMessageType> EXAMINE_TYPES =
      EnumSet.of(
          ChatMessageType.ITEM_EXAMINE,
          ChatMessageType.NPC_EXAMINE,
          ChatMessageType.OBJECT_EXAMINE);

  private final DialogueTextCleaner textCleaner;
  private final SynthesisDispatcher dispatcher;

  private final BooleanSupplier enabled;

  private final BooleanSupplier dialogueOpen;

  private final Consumer<Runnable> defer;

  public ExamineSpeaker(
      DialogueTextCleaner textCleaner,
      SynthesisDispatcher dispatcher,
      BooleanSupplier enabled,
      BooleanSupplier dialogueOpen,
      Consumer<Runnable> defer) {
    this.textCleaner = textCleaner;
    this.dispatcher = dispatcher;
    this.enabled = enabled;
    this.dialogueOpen = dialogueOpen;
    this.defer = defer;
  }

  public void onChatMessage(ChatMessage event) {
    if (!EXAMINE_TYPES.contains(event.getType()) || !enabled.getAsBoolean()) {
      return;
    }
    String message = event.getMessage();
    if (message == null) {
      return;
    }
    MessageNode node = event.getMessageNode();
    defer.accept(() -> speakIfGameAuthored(node, message));
  }

  private void speakIfGameAuthored(MessageNode node, String message) {
    if (node != null && node.getRuneLiteFormatMessage() != null) {
      return;
    }
    if (dialogueOpen.getAsBoolean()) {
      return;
    }
    String cleaned = textCleaner.clean(message);
    if (cleaned.isEmpty()) {
      return;
    }
    dispatcher.speakNarration(cleaned);
  }
}
