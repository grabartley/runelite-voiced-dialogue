package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
public final class SynthesisRequest {

  private final String text;
  private final VoiceSpec voice;
  private final Emotion emotion;
  private final CharacterProfile profile;
  private final boolean skipTranslation;
  private final boolean player;
  private final boolean prefetch;

  private SynthesisRequest(
      String text,
      VoiceSpec voice,
      Emotion emotion,
      CharacterProfile profile,
      boolean skipTranslation,
      boolean player,
      boolean prefetch) {
    this.text = text;
    this.voice = voice;
    this.emotion = emotion;
    this.profile = profile;
    this.skipTranslation = skipTranslation;
    this.player = player;
    this.prefetch = prefetch;
  }

  public SynthesisRequest(
      String text,
      VoiceSpec voice,
      Emotion emotion,
      CharacterProfile profile,
      boolean skipTranslation,
      boolean player) {
    this(text, voice, emotion, profile, skipTranslation, player, false);
  }

  public SynthesisRequest withEmotion(Emotion newEmotion) {
    if (newEmotion == emotion) {
      return this;
    }
    return new SynthesisRequest(
        text, voice, newEmotion, profile, skipTranslation, player, prefetch);
  }

  public SynthesisRequest asPrefetch() {
    if (prefetch) {
      return this;
    }
    return new SynthesisRequest(text, voice, emotion, profile, skipTranslation, player, true);
  }
}
