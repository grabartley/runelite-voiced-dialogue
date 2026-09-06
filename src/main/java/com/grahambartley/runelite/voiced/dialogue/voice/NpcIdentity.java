package com.grahambartley.runelite.voiced.dialogue.voice;

import com.grahambartley.runelite.voiced.dialogue.data.NpcAttributes;
import com.grahambartley.runelite.voiced.dialogue.data.NpcProfileTable;
import lombok.Value;
import lombok.experimental.Accessors;

/** Everything a line's voice and profile both need to know about the NPC speaking. */
@Value
@Accessors(fluent = true)
class NpcIdentity {

  /** The id of the matching NPC in the world list, or {@code null} when there is none. */
  Integer worldId;

  /** Detected attributes, or {@code null} when the NPC was not found or could not be analysed. */
  NpcAttributes attributes;

  /** The keyword categories the display name matches. */
  NpcProfileTable.NameMatch nameMatch;
}
