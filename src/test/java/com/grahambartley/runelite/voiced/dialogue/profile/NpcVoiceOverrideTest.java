package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import org.junit.Test;

public class NpcVoiceOverrideTest {

  @Test
  public void anOverrideWithNothingSetIsEmpty() {
    NpcVoiceOverride override = new NpcVoiceOverride(null, null, null, null, null);
    assertTrue(override.isEmpty());
    assertFalse(override.hasProfileFields());
  }

  @Test
  public void eachProfileFieldCountsAsAProfileField() {
    assertTrue(new NpcVoiceOverride("n", null, null, null, null).hasProfileFields());
    assertTrue(new NpcVoiceOverride(null, "a", null, null, null).hasProfileFields());
    assertTrue(new NpcVoiceOverride(null, null, "s", null, null).hasProfileFields());
    assertTrue(new NpcVoiceOverride(null, null, null, "p", null).hasProfileFields());
  }

  @Test
  public void aGenderOnlyOverrideIsNotEmptyButHasNoProfileFields() {
    NpcVoiceOverride override = new NpcVoiceOverride(null, null, null, null, NpcGender.FEMALE);
    assertFalse(override.isEmpty());
    assertFalse(override.hasProfileFields());
  }
}
