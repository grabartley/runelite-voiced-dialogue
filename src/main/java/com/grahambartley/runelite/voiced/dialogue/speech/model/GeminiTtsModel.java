package com.grahambartley.runelite.voiced.dialogue.speech.model;

import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.RawPcmDecoder;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import java.util.EnumSet;

public final class GeminiTtsModel {

  public static final String GEMINI_MODEL_ID = "gemini-3.1-flash-tts-preview";

  static final String MODEL_ID = "google/" + GEMINI_MODEL_ID;

  static final String RESPONSE_FORMAT = "pcm";

  static final int SAMPLE_RATE = 24_000;

  private final GeminiVoiceMap voiceMap = new GeminiVoiceMap();

  public String modelId() {
    return MODEL_ID;
  }

  public String responseFormat() {
    return RESPONSE_FORMAT;
  }

  public EnumSet<Emotion> supportedEmotions() {
    return EnumSet.copyOf(GeminiEmotionStyle.SUPPORTED);
  }

  public String voiceFor(VoiceSpec voice) {
    return voiceMap.voiceFor(voice);
  }

  public String styleInput(String text, Emotion emotion) {
    return GeminiEmotionStyle.apply(text, emotion);
  }

  public Pcm decodeResponse(byte[] bytes) {
    return RawPcmDecoder.decode(bytes, SAMPLE_RATE);
  }

  public int sampleRate() {
    return SAMPLE_RATE;
  }
}
