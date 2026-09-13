package com.grahambartley.runelite.voiced.dialogue.capture;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

public final class DialogueWidgetReader {

  static final int NO_EXPRESSION = -1;

  private static final String UNKNOWN_NPC = "Unknown NPC";

  private final Client client;

  public DialogueWidgetReader(Client client) {
    this.client = client;
  }

  static boolean isVisible(Widget widget) {
    return widget != null && !widget.isHidden();
  }

  int headAnimationId(int headWidgetId) {
    Widget head = client.getWidget(headWidgetId);
    if (head == null) {
      return NO_EXPRESSION;
    }
    return head.getAnimationId();
  }

  String currentNpcName() {
    Widget npcNameWidget = client.getWidget(InterfaceID.ChatLeft.NAME);
    if (isVisible(npcNameWidget)) {
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
