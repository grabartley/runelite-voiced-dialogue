package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.profile.Speaker;
import com.grahambartley.runelite.voiced.dialogue.speech.DialogueAudioService;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Scans the dialogue widgets each game tick and drives the speak/prefetch/interrupt flow: speaks a
 * new NPC or player line once (deduped per speaker against the last text that speaker said), warms
 * the visible options, and edge-triggers the close interrupt so audio is cut only on the
 * open-&gt;closed transition (not on every idle tick, which would truncate public-chat clips played
 * while walking around). Reads the client only on the game thread.
 *
 * <p>The narration boxes are scanned by the {@link NarrationWatcher} this owns, so they share that
 * one interrupt edge with the chat widgets rather than racing it from a second subscriber.
 *
 * <p>Also the single owner of whether a dialogue is open ({@link #isDialogueOpen()}), which the
 * chat-driven speakers read to keep the audio channel for the conversation in front of the player.
 */
public final class DialogueWatcher {

  private final Client client;
  private final DialogueTextCleaner textCleaner;
  private final DialogueWidgetReader widgetReader;
  private final SynthesisDispatcher dispatcher;
  private final DialoguePrefetchCoordinator prefetchCoordinator;
  private final DialogueAudioService audioService;
  private final NarrationWatcher narrationWatcher;

  private final Map<Speaker, String> lastSpokenBySpeaker = new EnumMap<>(Speaker.class);

  private boolean dialogueOpen;
  private boolean wasFullyClosed;

  public DialogueWatcher(
      Client client,
      DialogueTextCleaner textCleaner,
      DialogueWidgetReader widgetReader,
      SynthesisDispatcher dispatcher,
      DialoguePrefetchCoordinator prefetchCoordinator,
      DialogueAudioService audioService,
      NarrationWatcher narrationWatcher) {
    this.client = client;
    this.textCleaner = textCleaner;
    this.widgetReader = widgetReader;
    this.dispatcher = dispatcher;
    this.prefetchCoordinator = prefetchCoordinator;
    this.audioService = audioService;
    this.narrationWatcher = narrationWatcher;
  }

  public void tick() {
    Widget npcDialogue = client.getWidget(InterfaceID.ChatLeft.TEXT);
    boolean npcVisible = npcDialogue != null && !npcDialogue.isHidden();
    Widget playerDialogue = client.getWidget(InterfaceID.ChatRight.TEXT);
    boolean playerVisible = playerDialogue != null && !playerDialogue.isHidden();

    if (npcVisible) {
      speakIfNew(npcDialogue, InterfaceID.ChatLeft.HEAD, Speaker.NPC, widgetReader::currentNpcName);
    }
    if (playerVisible) {
      speakIfNew(playerDialogue, InterfaceID.ChatRight.HEAD, Speaker.PLAYER, () -> null);
    }

    boolean narrationVisible = narrationWatcher.tick();

    Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
    boolean optionsVisible = options != null && !options.isHidden();
    if (optionsVisible) {
      prefetchCoordinator.prefetchOptions(options);
    }

    boolean open = npcVisible || playerVisible || narrationVisible;
    if (shouldInterruptOnClose(open, dialogueOpen)) {
      audioService.interrupt();
      lastSpokenBySpeaker.clear();
      narrationWatcher.reset();
    }
    dialogueOpen = open;

    // Reset prefetch only once the dialogue is fully gone (no text and no option list), so the
    // session cap and queued warming survive the option-select screen. Edge-triggered like the
    // interrupt above, because "fully closed" is also the state of every idle tick spent walking
    // around, and re-cancelling on each of those would churn for nothing.
    boolean fullyClosed = !open && !optionsVisible;
    if (fullyClosed && !wasFullyClosed) {
      prefetchCoordinator.reset();
    }
    wasFullyClosed = fullyClosed;
  }

  /**
   * Whether a dialogue, option list aside, was open as of the last game tick. The single owner of
   * that question, so a feature that must yield the audio channel to dialogue consults this instead
   * of reading the same widgets a second time and drifting from it.
   *
   * <p>Sampled per tick, so between ticks it can lag the client by one. That is why it gates speech
   * the player has just triggered, where a tick of lag is unnoticeable, and never the interrupt,
   * which is edge-triggered from the live scan above.
   */
  public boolean isDialogueOpen() {
    return dialogueOpen;
  }

  private void speakIfNew(
      Widget dialogue, int headWidgetId, Speaker speaker, Supplier<String> npcName) {
    String text = dialogue.getText();
    if (text == null || text.isEmpty() || text.equals(lastSpokenBySpeaker.get(speaker))) {
      return;
    }
    lastSpokenBySpeaker.put(speaker, text);
    dispatcher.speakDialogue(
        textCleaner.clean(text),
        speaker,
        npcName.get(),
        widgetReader.headAnimationId(headWidgetId));
  }

  /**
   * Pure decision for the close interrupt: cut audio only on the open-&gt;closed transition, so the
   * idle ticks while the player walks around (no dialogue open) never interrupt a playing
   * public-chat clip. Factored out so it is unit-testable without a live client.
   */
  static boolean shouldInterruptOnClose(boolean dialogueOpen, boolean wasDialogueOpen) {
    return wasDialogueOpen && !dialogueOpen;
  }
}
