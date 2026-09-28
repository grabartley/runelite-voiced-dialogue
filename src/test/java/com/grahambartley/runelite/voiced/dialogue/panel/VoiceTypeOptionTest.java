package com.grahambartley.runelite.voiced.dialogue.panel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.grahambartley.runelite.voiced.dialogue.profile.VoiceType;
import org.junit.Test;

public class VoiceTypeOptionTest {

  @Test
  public void pluginDefaultCarriesNoVoiceType() {
    assertNull(VoiceTypeOption.PLUGIN_DEFAULT.voiceType());
    assertEquals("Plugin default", VoiceTypeOption.PLUGIN_DEFAULT.toString());
  }

  @Test
  public void theTypesMatchThePlayerVoiceChoice() {
    assertEquals(VoiceType.TYPE_A, VoiceTypeOption.TYPE_A.voiceType());
    assertEquals(VoiceType.TYPE_B, VoiceTypeOption.TYPE_B.voiceType());
    assertEquals("Type A", VoiceTypeOption.TYPE_A.toString());
    assertEquals("Type B", VoiceTypeOption.TYPE_B.toString());
  }

  @Test
  public void ofMapsBackFromAVoiceType() {
    assertEquals(VoiceTypeOption.PLUGIN_DEFAULT, VoiceTypeOption.of(null));
    assertEquals(VoiceTypeOption.TYPE_A, VoiceTypeOption.of(VoiceType.TYPE_A));
    assertEquals(VoiceTypeOption.TYPE_B, VoiceTypeOption.of(VoiceType.TYPE_B));
  }

  @Test
  public void pluginDefaultIsListedFirst() {
    assertEquals(VoiceTypeOption.PLUGIN_DEFAULT, VoiceTypeOption.values()[0]);
  }
}
