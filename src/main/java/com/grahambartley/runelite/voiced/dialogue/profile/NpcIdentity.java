package com.grahambartley.runelite.voiced.dialogue.profile;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcAttributes;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
class NpcIdentity {

  Integer worldId;

  NpcAttributes attributes;

  NpcProfileTable.NameMatch nameMatch;
}
