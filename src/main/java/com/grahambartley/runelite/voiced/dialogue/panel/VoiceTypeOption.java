package com.grahambartley.runelite.voiced.dialogue.panel;

import com.grahambartley.runelite.voiced.dialogue.profile.VoiceType;

enum VoiceTypeOption {
  PLUGIN_DEFAULT(null, "Plugin default"),
  TYPE_A(VoiceType.TYPE_A, VoiceType.TYPE_A.toString()),
  TYPE_B(VoiceType.TYPE_B, VoiceType.TYPE_B.toString());

  private final VoiceType voiceType;
  private final String label;

  VoiceTypeOption(VoiceType voiceType, String label) {
    this.voiceType = voiceType;
    this.label = label;
  }

  VoiceType voiceType() {
    return voiceType;
  }

  static VoiceTypeOption of(VoiceType voiceType) {
    for (VoiceTypeOption option : values()) {
      if (option.voiceType == voiceType) {
        return option;
      }
    }
    return PLUGIN_DEFAULT;
  }

  @Override
  public String toString() {
    return label;
  }
}
