package com.grahambartley.runelite.voiced.dialogue.panel;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public interface NpcNameResolver {
  void resolve(Set<Integer> npcIds, Consumer<Map<Integer, String>> onResolved);
}
