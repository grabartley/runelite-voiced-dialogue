package com.grahambartley.runelite.voiced.dialogue.profile;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class NpcVoiceImport {

  Map<Integer, NpcVoiceOverride> overrides;
  int skipped;

  public NpcVoiceImport(Map<Integer, NpcVoiceOverride> overrides, int skipped) {
    this.overrides = Collections.unmodifiableMap(new TreeMap<>(overrides));
    this.skipped = skipped;
  }
}
