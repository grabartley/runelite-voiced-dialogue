package com.grahambartley.runelite.voiced.dialogue.speaker;

import net.runelite.api.Client;
import net.runelite.api.NPC;

public final class NpcFinder {

  private final Client client;

  public NpcFinder(Client client) {
    this.client = client;
  }

  public NPC findByName(String targetName) {
    if (client.getNpcs() == null) {
      return null;
    }

    String wanted = NameNormalizer.normalize(targetName);
    if (wanted.isEmpty()) {
      return null;
    }

    return client.getNpcs().stream()
        .filter(npc -> npc != null && npc.getName() != null)
        .filter(npc -> NameNormalizer.normalize(npc.getName()).equalsIgnoreCase(wanted))
        .findFirst()
        .orElse(null);
  }
}
