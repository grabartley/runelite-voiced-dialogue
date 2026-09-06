package com.grahambartley.runelite.voiced.dialogue.profile;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Everything one line's speaker resolves to: the backend-neutral voice and the character profile
 * steering the delivery.
 */
@Value
@Accessors(fluent = true)
public class ResolvedSpeaker {

  VoiceSpec voice;

  /** The delivery profile, or {@code null} when character profiles are off. */
  CharacterProfile profile;
}
