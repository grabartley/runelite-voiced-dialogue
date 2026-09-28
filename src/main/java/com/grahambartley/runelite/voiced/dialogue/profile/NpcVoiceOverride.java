package com.grahambartley.runelite.voiced.dialogue.profile;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class NpcVoiceOverride {

  String name;
  String accent;
  String style;
  String pace;
  VoiceType voiceType;

  public boolean hasProfileFields() {
    return name != null || accent != null || style != null || pace != null;
  }

  public boolean isEmpty() {
    return !hasProfileFields() && voiceType == null;
  }
}
