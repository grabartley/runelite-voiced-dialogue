package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;

/**
 * Mutable {@link VoicedDialogueConfig} for synthesis tests: settable API keys, char cap, pace,
 * language, and speaking styles. Everything not overridden keeps the interface default.
 */
public final class MutableTestConfig implements VoicedDialogueConfig {

  public String openRouterKey = "";
  public String googleAiStudioKey = "";
  public int maxChars;
  public int speedPercent = 100;
  public VoicedDialogueConfig.SpokenLanguage language = VoicedDialogueConfig.SpokenLanguage.ENGLISH;
  public VoicedDialogueConfig.SpeakingStyle playerQuirk = VoicedDialogueConfig.SpeakingStyle.NONE;
  public VoicedDialogueConfig.SpeakingStyle npcQuirk = VoicedDialogueConfig.SpeakingStyle.NONE;

  @Override
  public String openRouterApiKey() {
    return openRouterKey;
  }

  @Override
  public String googleAiStudioApiKey() {
    return googleAiStudioKey;
  }

  @Override
  public int cloudMaxChars() {
    return maxChars;
  }

  @Override
  public int speakingPace() {
    return speedPercent;
  }

  @Override
  public VoicedDialogueConfig.SpokenLanguage cloudLanguage() {
    return language;
  }

  @Override
  public VoicedDialogueConfig.SpeakingStyle cloudPlayerSpeakingStyle() {
    return playerQuirk;
  }

  @Override
  public VoicedDialogueConfig.SpeakingStyle cloudNpcSpeakingStyle() {
    return npcQuirk;
  }
}
