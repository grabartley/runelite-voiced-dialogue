package com.grahambartley.runelite.voiced.dialogue.panel;

import java.util.List;
import java.util.Set;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
class NpcListing {
  List<NpcListEntry> heard;
  List<NpcListEntry> edited;
  List<NpcListEntry> matches;
  boolean truncated;
  Set<Integer> unnamedEditedIds;

  boolean searching() {
    return matches != null;
  }
}
