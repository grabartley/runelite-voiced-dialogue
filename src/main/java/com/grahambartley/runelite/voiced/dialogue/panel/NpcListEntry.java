package com.grahambartley.runelite.voiced.dialogue.panel;

import java.util.List;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
class NpcListEntry {
  String name;
  List<Integer> ids;
  boolean heard;
  boolean edited;
  int preferredId;
}
