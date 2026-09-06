package com.grahambartley.runelite.voiced.dialogue.dialogue;

import com.grahambartley.runelite.voiced.dialogue.synthesis.SynthesisDispatcher;
import com.grahambartley.runelite.voiced.dialogue.tts.DialogueAudioService;
import com.grahambartley.runelite.voiced.dialogue.voice.Speaker;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Scans the dialogue widgets each game tick and drives the speak/prefetch/interrupt flow: speaks a
 * new NPC or player line once (deduped against the last spoken text), warms the visible options,
 * and edge-triggers the close interrupt so audio is cut only on the open-&gt;closed transition (not
 * on every idle tick, which would truncate public-chat clips played while walking around). Reads
 * the client only on the game thread.
 */
public final class DialogueWatcher {

  private final Client client;
  private final DialogueTextCleaner textCleaner;
  private final DialogueWidgetReader widgetReader;
  private final SynthesisDispatcher dispatcher;
  private final DialoguePrefetchCoordinator prefetchCoordinator;
  private final DialogueAudioService audioService;

  private String lastSpoken = "";
  private boolean wasDialogueOpen;
  private boolean wasFullyClosed;

  public DialogueWatcher(
      Client client,
      DialogueTextCleaner textCleaner,
      DialogueWidgetReader widgetReader,
      SynthesisDispatcher dispatcher,
      DialoguePrefetchCoordinator prefetchCoordinator,
      DialogueAudioService audioService) {
    this.client = client;
    this.textCleaner = textCleaner;
    this.widgetReader = widgetReader;
    this.dispatcher = dispatcher;
    this.prefetchCoordinator = prefetchCoordinator;
    this.audioService = audioService;
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

    Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
    boolean optionsVisible = options != null && !options.isHidden();
    if (optionsVisible) {
      prefetchCoordinator.prefetchOptions(options);
    }

    boolean dialogueOpen = npcVisible || playerVisible;
    if (shouldInterruptOnClose(dialogueOpen, wasDialogueOpen)) {
      audioService.interrupt();
      lastSpoken = "";
    }
    wasDialogueOpen = dialogueOpen;

    // Reset prefetch only once the dialogue is fully gone (no text and no option list), so the
    // session cap and queued warming survive the option-select screen. Edge-triggered like the
    // interrupt above, because "fully closed" is also the state of every idle tick spent walking
    // around, and re-cancelling on each of those would churn for nothing.
    boolean fullyClosed = !dialogueOpen && !optionsVisible;
    if (fullyClosed && !wasFullyClosed) {
      prefetchCoordinator.reset();
    }
    wasFullyClosed = fullyClosed;
  }

  private void speakIfNew(
      Widget dialogue, int headWidgetId, Speaker speaker, Supplier<String> npcName) {
    String text = dialogue.getText();
    if (text == null || text.isEmpty() || text.equals(lastSpoken)) {
      return;
    }
    lastSpoken = text;
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
