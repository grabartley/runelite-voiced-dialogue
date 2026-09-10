package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;

/**
 * Voices examine text (default off): the flavour line the game prints when the player examines an
 * item, an NPC, or a piece of scenery. That is the game describing its world rather than a
 * character speaking, so it is voiced through the narrator, sharing its voice, profile and cache
 * namespace rather than introducing a second narration concept.
 *
 * <p>The client tags examines with their own chat types, so no other game-channel message can reach
 * this path and no string matching is involved. The types alone are not enough, though: other
 * plugins publish their own lines on them, so only messages the game itself authored are voiced
 * (see {@link #gameAuthored}). Reads the client only on the game thread.
 */
public final class ExamineSpeaker {

  /** The chat types the client reserves for examine text. */
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
   * conversation in front of the player rather than talking over it. A narration box counts only
   * while narration is being voiced, since a box nobody is voicing holds no audio channel.
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
    if (!gameAuthored(event.getMessageNode())) {
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

  /**
   * Whether the game wrote this line, rather than a plugin publishing on the same chat type. The
   * client stamps a RuneLite format message onto any node a plugin authored or reformatted, and
   * leaves it null on the game's own text, so this separates the two without inspecting a single
   * word.
   *
   * <p>It is what keeps the item-price lookups that RuneLite's own Examine plugin appends off the
   * narrator. Those arrive on {@code ITEM_EXAMINE} in the same tick as the real examine text, and
   * because each new line stops the one playing, voicing them would talk over the flavour line the
   * player actually asked for.
   *
   * <p>A null node is treated as game-authored: the field is absent rather than stamped, and the
   * game's own messages are the ones that must never be dropped.
   */
  private static boolean gameAuthored(MessageNode node) {
    return node == null || node.getRuneLiteFormatMessage() == null;
  }
}
