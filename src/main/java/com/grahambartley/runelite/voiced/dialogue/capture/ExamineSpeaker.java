package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;

/**
 * Voices examine text (default off): the flavour line the game prints when the player examines an
 * item, an NPC, or a piece of scenery. That is the game describing its world rather than a
 * character speaking, so it is voiced through the narrator, sharing its voice, profile and cache
 * namespace rather than introducing a second narration concept.
 *
 * <p>The client tags examines with their own chat types, so no other game-channel message can reach
 * this path and no string matching is involved. Reads the client only on the game thread.
 */
public final class ExamineSpeaker {

  /**
   * The chat types the client reserves for examine text. Held as a set rather than a chain of
   * comparisons so adding a type is a one-line data change.
   */
  private static final Set<ChatMessageType> EXAMINE_TYPES =
      EnumSet.of(
          ChatMessageType.ITEM_EXAMINE,
          ChatMessageType.NPC_EXAMINE,
          ChatMessageType.OBJECT_EXAMINE);

  private final DialogueTextCleaner textCleaner;
  private final SynthesisDispatcher dispatcher;

  /** The feature toggle, read live so flipping it takes effect immediately. */
  private final BooleanSupplier enabled;

  /**
   * Whether a dialogue is open, owned by {@link DialogueWatcher}. Examine yields to the
   * conversation in front of the player rather than talking over it.
   */
  private final BooleanSupplier dialogueOpen;

  public ExamineSpeaker(
      DialogueTextCleaner textCleaner,
      SynthesisDispatcher dispatcher,
      BooleanSupplier enabled,
      BooleanSupplier dialogueOpen) {
    this.textCleaner = textCleaner;
    this.dispatcher = dispatcher;
    this.enabled = enabled;
    this.dialogueOpen = dialogueOpen;
  }

  public void onChatMessage(ChatMessage event) {
    if (!enabled.getAsBoolean() || !EXAMINE_TYPES.contains(event.getType())) {
      return;
    }
    if (dialogueOpen.getAsBoolean()) {
      return;
    }
    String message = event.getMessage();
    if (message == null) {
      return;
    }
    String cleaned = textCleaner.clean(message);
    if (cleaned.isEmpty()) {
      return;
    }
    dispatcher.speakNarration(cleaned);
  }
}
