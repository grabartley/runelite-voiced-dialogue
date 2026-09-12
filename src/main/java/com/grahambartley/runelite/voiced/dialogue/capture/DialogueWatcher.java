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
    boolean npcVisible = isVisible(npcDialogue);
    Widget playerDialogue = client.getWidget(InterfaceID.ChatRight.TEXT);
    boolean playerVisible = isVisible(playerDialogue);

    if (npcVisible) {
      speakIfNew(npcDialogue, InterfaceID.ChatLeft.HEAD, Speaker.NPC, widgetReader::currentNpcName);
    }
    if (playerVisible) {
      speakIfNew(playerDialogue, InterfaceID.ChatRight.HEAD, Speaker.PLAYER, () -> null);
    }

    boolean narrationVisible = narrationWatcher.tick();

    Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
    boolean optionsVisible = isVisible(options);
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

    boolean fullyClosed = !open && !optionsVisible;
    if (fullyClosed && !wasFullyClosed) {
      prefetchCoordinator.reset();
    }
    wasFullyClosed = fullyClosed;
  }

  public boolean isDialogueOpen() {
    return dialogueOpen;
  }

  public boolean isConversationOnScreen() {
    return dialogueOpen
        || isVisible(client.getWidget(InterfaceID.ChatLeft.TEXT))
        || isVisible(client.getWidget(InterfaceID.ChatRight.TEXT))
        || isVisible(client.getWidget(InterfaceID.Chatmenu.OPTIONS))
        || narrationWatcher.isOnScreen();
  }

  private static boolean isVisible(Widget widget) {
    return widget != null && !widget.isHidden();
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

  static boolean shouldInterruptOnClose(boolean dialogueOpen, boolean wasDialogueOpen) {
    return wasDialogueOpen && !dialogueOpen;
  }
}
