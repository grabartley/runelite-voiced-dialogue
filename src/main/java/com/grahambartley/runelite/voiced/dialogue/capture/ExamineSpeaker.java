package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
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
 * this path and no wording is ever inspected. The types alone are not enough, though: other plugins
 * publish their own lines on them, and only the ones the game itself wrote are voiced. See {@link
 * #onChatMessage} for why that decision has to wait a tick.
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

  /** Runs a task on the client thread one tick later. */
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

  /**
   * Takes an examine line and decides whether to voice it one tick later.
   *
   * <p>The delay is what makes the game-authored test work at all. The client marks a plugin's line
   * by stamping a RuneLite format message onto its node, but it does that <em>after</em> publishing
   * the message, so every subscriber first sees the node unmarked. Deciding on arrival would
   * therefore voice a plugin's line as though the game had written it. One client tick is roughly
   * 20ms, long enough for the stamp to have landed and short enough that nobody hears the wait.
   *
   * <p>What that buys: RuneLite's own Examine plugin appends an item price on {@code ITEM_EXAMINE}
   * moments after the real examine text, and because every new line stops the one playing, voicing
   * the price would cut off the flavour line the player actually asked for.
   */
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

  /**
   * Voices the line unless a plugin authored it. The dialogue gate is checked here rather than on
   * arrival because this is the moment the audio channel would actually be taken.
   *
   * <p>A null node is treated as game-authored: the mark is absent rather than present, and the
   * game's own text is what must never be dropped.
   */
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
