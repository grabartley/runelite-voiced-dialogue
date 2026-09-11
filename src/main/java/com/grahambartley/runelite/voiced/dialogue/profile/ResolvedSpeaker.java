package com.grahambartley.runelite.voiced.dialogue.profile;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class ResolvedSpeaker {

  VoiceSpec voice;

  CharacterProfile profile;
}
