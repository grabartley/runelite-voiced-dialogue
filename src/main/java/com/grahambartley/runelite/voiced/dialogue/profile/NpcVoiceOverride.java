package com.grahambartley.runelite.voiced.dialogue.profile;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class NpcVoiceOverride {

  String name;
  String accent;
  String style;
  String pace;
  NpcGender gender;

  public boolean hasProfileFields() {
    return name != null || accent != null || style != null || pace != null;
  }

  public boolean isEmpty() {
    return !hasProfileFields() && gender == null;
  }
}
