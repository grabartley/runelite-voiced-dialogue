package com.grahambartley.runelite.voiced.dialogue.dialogue;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Reads the dialogue chat-head animation id and the speaking NPC's name from the client. Only
 * touches the client on the game thread; never throws. Kept separate from the synthesis decision
 * logic so that logic stays unit-testable without a live client.
 */
public final class DialogueWidgetReader {

  /**
   * Sentinel head-animation id meaning "no detectable expression": a missing head widget (sprite /
   * objectbox dialogue) or the one-tick race where the head animation lags the text. Resolves to
   * {@link com.grahambartley.runelite.voiced.dialogue.synthesis.Emotion#NEUTRAL}, matching the
   * engine's own {@code -1}.
   */
  static final int NO_EXPRESSION = -1;

  private static final String UNKNOWN_NPC = "Unknown NPC";

  private final Client client;

  public DialogueWidgetReader(Client client) {
    this.client = client;
  }

  /**
   * Sprite and objectbox dialogues carry no chat head at all, so an absent head widget yields
   * {@link #NO_EXPRESSION} and the caller resolves NEUTRAL.
   */
  int headAnimationId(int headWidgetId) {
    Widget head = client.getWidget(headWidgetId);
    if (head == null) {
      return NO_EXPRESSION;
    }
    return head.getAnimationId();
  }

  String currentNpcName() {
    Widget npcNameWidget = client.getWidget(InterfaceID.ChatLeft.NAME);
    if (npcNameWidget != null && !npcNameWidget.isHidden()) {
      String npcName = npcNameWidget.getText();
      if (npcName != null && !npcName.isEmpty()) {
        return npcName.trim();
      }
    }

    Player local = client.getLocalPlayer();
    if (local != null && local.getInteracting() != null) {
      String interactingName = local.getInteracting().getName();
      if (interactingName != null && !interactingName.isEmpty()) {
        return interactingName.trim();
      }
    }

    return UNKNOWN_NPC;
  }
}
