package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import org.junit.Test;

public class VoiceTypeTest {

  @Test
  public void eachTypeFixesAVoiceGender() {
    assertEquals(NpcGender.MALE, VoiceType.TYPE_A.getGender());
    assertEquals(NpcGender.FEMALE, VoiceType.TYPE_B.getGender());
  }

  @Test
  public void theDropdownShowsTheTypeLabels() {
    assertEquals("Type A", VoiceType.TYPE_A.toString());
    assertEquals("Type B", VoiceType.TYPE_B.toString());
  }

  @Test
  public void theStoredNamesStayStableForSavedSettings() {
    assertEquals(VoiceType.TYPE_A, VoiceType.valueOf("TYPE_A"));
    assertEquals(VoiceType.TYPE_B, VoiceType.valueOf("TYPE_B"));
  }
}
