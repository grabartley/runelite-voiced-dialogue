package com.grahambartley.runelite.voiced.dialogue;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.util.Text;

final class SetVoiceMenu {

  static final String OPTION = "Set voice";

  interface NpcOpener {
    void open(int npcId, String name);
  }

  private final Client client;
  private final BooleanSupplier enabled;
  private final Function<NPC, Integer> profileId;
  private final IntPredicate speaks;
  private final Consumer<Runnable> uiThread;
  private final NpcOpener opener;

  SetVoiceMenu(
      Client client,
      BooleanSupplier enabled,
      Function<NPC, Integer> profileId,
      IntPredicate speaks,
      Consumer<Runnable> uiThread,
      NpcOpener opener) {
    this.client = client;
    this.enabled = enabled;
    this.profileId = profileId;
    this.speaks = speaks;
    this.uiThread = uiThread;
    this.opener = opener;
  }

  void onMenuEntryAdded(MenuEntryAdded event) {
    MenuEntry added = event.getMenuEntry();
    if (!enabled.getAsBoolean() || added.getType() != MenuAction.EXAMINE_NPC) {
      return;
    }
    NPC npc = added.getNpc();
    if (npc == null) {
      return;
    }
    Integer npcId = profileId.apply(npc);
    if (npcId == null || !speaks.test(npcId)) {
      return;
    }
    client
        .getMenu()
        .createMenuEntry(-1)
        .setOption(OPTION)
        .setTarget(event.getTarget())
        .setIdentifier(event.getIdentifier())
        .setType(MenuAction.RUNELITE)
        .onClick(clicked -> open(npcId, npc));
  }

  private void open(int npcId, NPC npc) {
    String name = npc.getName() == null ? "" : Text.removeTags(npc.getName()).trim();
    if (name.isEmpty()) {
      return;
    }
    uiThread.accept(() -> opener.open(npcId, name));
  }
}
