package com.grahambartley.runelite.voiced.dialogue.voice;

import com.grahambartley.runelite.voiced.dialogue.data.NpcDemographicAnalyzer;
import com.grahambartley.runelite.voiced.dialogue.data.NpcProfileTable;
import net.runelite.api.NPC;

/**
 * Resolves a dialogue name to the NPC behind it once per line: the world entity, its detected
 * attributes, and the keyword categories its name matches. The voice and the character profile then
 * read that one result, so a line costs a single world scan and a single table lookup no matter how
 * many decisions ride on it.
 */
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
      return new NpcIdentity(null, null, nameMatch);
    }
    return new NpcIdentity(npc.getId(), demographicAnalyzer.analyzeNPC(npc), nameMatch);
  }
}
