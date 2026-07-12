package com.grahambartley.dialogue;

import com.grahambartley.synthesis.SynthesisDispatcher;
import com.grahambartley.tts.DialogueAudioService;
import com.grahambartley.voice.VoiceManager;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;

/**
 * Scans the dialogue widgets each client tick and drives the speak/prefetch/interrupt flow: speaks
 * a new NPC or player line once (deduped against the last spoken text), warms the visible options,
 * and edge-triggers the close interrupt so audio is cut only on the open-&gt;closed transition (not
 * on every idle tick, which would truncate public-chat clips played while walking around). Reads
 * the client only on the game thread.
 */
public final class DialogueWatcher {

  /** Allows transient blank widgets before treating dialogue as closed. */
  static final int CLOSE_DEBOUNCE_CLIENT_TICKS = 12;

  /** Waits briefly for a present chat head to receive its expression animation. */
  static final int HEAD_WAIT_MAX_CLIENT_TICKS = 8;

  private final Client client;
  private final DialogueTextCleaner textCleaner;
  private final DialogueWidgetReader widgetReader;
  private final SynthesisDispatcher dispatcher;
  private final DialoguePrefetchCoordinator prefetchCoordinator;
  private final DialoguePrefetcher prefetcher;
  private final DialogueAudioService audioService;

  private String lastSpoken = "";
  private String pendingLine;
  private String lastOptionsSignature;
  private boolean wasDialogueOpen;
  private int closedClientTicks;
  private long dialoguePlaybackEpoch = -1;
  private String sessionNpc;
  private String headWaitLineKey;
  private int headWaitTicks;

  public DialogueWatcher(
      Client client,
      DialogueTextCleaner textCleaner,
      DialogueWidgetReader widgetReader,
      SynthesisDispatcher dispatcher,
      DialoguePrefetchCoordinator prefetchCoordinator,
      DialoguePrefetcher prefetcher,
      DialogueAudioService audioService) {
    this.client = client;
    this.textCleaner = textCleaner;
    this.widgetReader = widgetReader;
    this.dispatcher = dispatcher;
    this.prefetchCoordinator = prefetchCoordinator;
    this.prefetcher = prefetcher;
    this.audioService = audioService;
  }

  public void tick() {
    Widget npcDialogue = client.getWidget(ComponentID.DIALOG_NPC_TEXT);
    Widget playerDialogue = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);
    boolean candidateVisible = false;
    if (npcDialogue != null && !npcDialogue.isHidden()) {
      String text = npcDialogue.getText();
      if (text != null && !text.isEmpty()) {
        candidateVisible = true;
        String npcName = widgetReader.currentNpcName();
        int headAnimationId = widgetReader.headAnimationId(InterfaceID.ChatLeft.HEAD);
        String lineKey = "npc\u0001" + npcName + "\u0001" + text;
        if (expressionReady(
            lineKey, headAnimationId, widgetReader.hasHead(InterfaceID.ChatLeft.HEAD))) {
          String sampleKey = lineKey + "\u0001" + headAnimationId;
          if (stableNewLine(lineKey, sampleKey)) {
            long previousEpoch = dialoguePlaybackEpoch;
            beginSessionNode(npcName);
            boolean spoken =
                dispatcher.speakDialogue(
                    textCleaner.clean(text), VoiceManager.SPEAKER_NPC, npcName, headAnimationId);
            if (spoken) {
              dialoguePlaybackEpoch = audioService.currentEpoch();
            } else if (previousEpoch >= 0) {
              audioService.interruptIfCurrent(previousEpoch);
              dialoguePlaybackEpoch = -1;
            }
          }
        }
      }
    } else if (playerDialogue != null && !playerDialogue.isHidden()) {
      String text = playerDialogue.getText();
      if (text != null && !text.isEmpty()) {
        candidateVisible = true;
        int headAnimationId = widgetReader.headAnimationId(InterfaceID.ChatRight.HEAD);
        String lineKey = "player\u0001" + text;
        if (expressionReady(
            lineKey, headAnimationId, widgetReader.hasHead(InterfaceID.ChatRight.HEAD))) {
          String sampleKey = lineKey + "\u0001" + headAnimationId;
          if (stableNewLine(lineKey, sampleKey)) {
            long previousEpoch = dialoguePlaybackEpoch;
            boolean spoken =
                dispatcher.speakDialogue(
                    textCleaner.clean(text), VoiceManager.SPEAKER_PLAYER, null, headAnimationId);
            if (spoken) {
              dialoguePlaybackEpoch = audioService.currentEpoch();
            } else if (previousEpoch >= 0) {
              audioService.interruptIfCurrent(previousEpoch);
              dialoguePlaybackEpoch = -1;
            }
          }
        }
      }
    }
    if (!candidateVisible) {
      pendingLine = null;
    }

    Widget options = client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS);
    boolean optionsVisible = options != null && !options.isHidden();
    if (optionsVisible) {
      String signature = optionsSignature(options);
      if (!signature.equals(lastOptionsSignature)) {
        lastOptionsSignature = signature;
        prefetchCoordinator.prefetchOptions(options);
      }
    } else {
      lastOptionsSignature = null;
    }

    boolean dialogueOpen =
        (npcDialogue != null && !npcDialogue.isHidden())
            || (playerDialogue != null && !playerDialogue.isHidden())
            || optionsVisible;
    if (dialogueOpen) {
      wasDialogueOpen = true;
      closedClientTicks = 0;
    } else if (wasDialogueOpen) {
      closedClientTicks++;
      if (shouldInterruptOnClose(false, true, closedClientTicks)) {
        if (dialoguePlaybackEpoch >= 0) {
          audioService.interruptIfCurrent(dialoguePlaybackEpoch);
        }
        lastSpoken = "";
        pendingLine = null;
        wasDialogueOpen = false;
        closedClientTicks = 0;
        dialoguePlaybackEpoch = -1;
        sessionNpc = null;
        headWaitLineKey = null;
        headWaitTicks = 0;
        prefetcher.reset();
      }
    }
  }

  /**
   * Pure decision for the close interrupt: cut audio only on the open-&gt;closed transition, so the
   * idle ticks while the player walks around (no dialogue open) never interrupt a playing
   * public-chat clip. Factored out so it is unit-testable without a live client.
   */
  static boolean shouldInterruptOnClose(
      boolean dialogueOpen, boolean wasDialogueOpen, int closedClientTicks) {
    return wasDialogueOpen && !dialogueOpen && closedClientTicks >= CLOSE_DEBOUNCE_CLIENT_TICKS;
  }

  /** Requires one stable client-tick repeat so text, name, and chat-head update together. */
  private boolean stableNewLine(String lineKey, String sampleKey) {
    if (lineKey.equals(lastSpoken)) {
      return false;
    }
    if (!sampleKey.equals(pendingLine)) {
      pendingLine = sampleKey;
      return false;
    }
    pendingLine = null;
    lastSpoken = lineKey;
    headWaitLineKey = null;
    headWaitTicks = 0;
    return true;
  }

  /** Wait briefly for a lagging chat-head animation; headless dialogue speaks as neutral. */
  private boolean expressionReady(String lineKey, int headAnimationId, boolean headPresent) {
    if (!headPresent || headAnimationId != DialogueWidgetReader.NO_EXPRESSION) {
      headWaitLineKey = null;
      headWaitTicks = 0;
      return true;
    }
    if (!lineKey.equals(headWaitLineKey)) {
      headWaitLineKey = lineKey;
      headWaitTicks = 1;
      return false;
    }
    headWaitTicks++;
    return headWaitTicks >= HEAD_WAIT_MAX_CLIENT_TICKS;
  }

  /**
   * Cancels queued work for the prior node and resets the prefetch session when speakers change.
   */
  private void beginSessionNode(String npcName) {
    if (sessionNpc != null
        && !sessionNpc.equals(npcName)
        && !"Unknown NPC".equals(npcName)
        && !"Unknown NPC".equals(sessionNpc)) {
      prefetcher.reset();
    } else {
      prefetcher.advanceNode();
    }
    if (!"Unknown NPC".equals(npcName)) {
      sessionNpc = npcName;
    }
  }

  private static String optionsSignature(Widget options) {
    Widget[] children = options.getDynamicChildren();
    if (children == null || children.length == 0) {
      return "";
    }
    StringBuilder signature = new StringBuilder();
    for (Widget child : children) {
      if (child != null) {
        signature.append(child.getText()).append('\u0001');
      }
    }
    return signature.toString();
  }
}
