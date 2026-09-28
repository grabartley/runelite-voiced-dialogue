package com.grahambartley.runelite.voiced.dialogue.profile;

import java.util.HashSet;
import java.util.Set;

public final class NpcVoiceImportPlan {

  public enum Mode {
    MERGE,
    REPLACE_ALL
  }

  private final NpcVoiceImport imported;
  private final Set<Integer> current;

  public NpcVoiceImportPlan(NpcVoiceImport imported, Set<Integer> current) {
    this.imported = imported;
    this.current = new HashSet<>(current);
  }

  public int sets() {
    return imported.overrides().size();
  }

  public int replaces() {
    int replaces = 0;
    for (Integer id : imported.overrides().keySet()) {
      if (current.contains(id)) {
        replaces++;
      }
    }
    return replaces;
  }

  public int skipped() {
    return imported.skipped();
  }

  public int clearedByReplaceAll() {
    return current.size() - replaces();
  }

  public void apply(NpcVoiceOverrideStore store, Mode mode) {
    if (mode == Mode.REPLACE_ALL) {
      for (Integer id : current) {
        if (!imported.overrides().containsKey(id)) {
          store.clear(id);
        }
      }
    }
    imported.overrides().forEach(store::set);
  }
}
