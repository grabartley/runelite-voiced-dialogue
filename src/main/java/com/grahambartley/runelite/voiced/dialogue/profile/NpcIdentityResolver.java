package com.grahambartley.runelite.voiced.dialogue.profile;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcDemographicAnalyzer;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcFinder;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;

final class NpcIdentityResolver {

  private final NpcFinder npcFinder;
  private final NpcDemographicAnalyzer demographicAnalyzer;
  private final NpcProfileTable profileTable;

  NpcIdentityResolver(
      NpcFinder npcFinder,
      NpcDemographicAnalyzer demographicAnalyzer,
      NpcProfileTable profileTable) {
    this.npcFinder = npcFinder;
    this.demographicAnalyzer = demographicAnalyzer;
    this.profileTable = profileTable;
  }

  NpcIdentity resolve(String npcName) {
    NpcProfileTable.NameMatch nameMatch = profileTable.matchName(npcName);
    NPC npc = npcFinder.findByName(npcName);
    if (npc == null) {
      return new NpcIdentity(null, null, null, nameMatch);
    }
    return new NpcIdentity(
        npc.getId(), baseId(npc), demographicAnalyzer.analyzeNPC(npc), nameMatch);
  }

  NpcIdentity resolve(NPC npc) {
    return new NpcIdentity(
        npc.getId(),
        baseId(npc),
        demographicAnalyzer.analyzeNPC(npc),
        profileTable.matchName(npc.getName()));
  }

  private static int baseId(NPC npc) {
    NPCComposition base = npc.getComposition();
    return base == null ? npc.getId() : base.getId();
  }
}
