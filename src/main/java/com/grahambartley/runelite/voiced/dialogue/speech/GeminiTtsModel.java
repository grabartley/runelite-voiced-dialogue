package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.RawPcmDecoder;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import java.util.EnumSet;

/**
 * The plugin's fixed speech model, Gemini 3.1 Flash TTS: the one cloud speech model with both a
 * voice catalog rich enough to map every race/gender and full emotion support. Voices come from
 * {@link GeminiVoiceMap}, emotion is rendered as an inline {@link GeminiEmotionStyle} tag, and the
 * {@code pcm} response is a raw headerless stream of signed 16-bit little-endian mono samples at 24
 * kHz, decoded by {@link RawPcmDecoder} at its true rate so playback is not pitch-shifted.
 */
final class GeminiTtsModel {

  /**
   * The bare Gemini API model name. The OpenRouter id below prefixes it with the {@code google/}
   * namespace, so a model bump cannot half-apply across the two providers.
   */
  static final String GEMINI_MODEL_ID = "gemini-3.1-flash-tts-preview";

  /** The OpenRouter model id sent as the {@code model} field. */
  static final String MODEL_ID = "google/" + GEMINI_MODEL_ID;

  static final String RESPONSE_FORMAT = "pcm";

  /** OpenRouter {@code pcm} output is headerless 16-bit LE mono at this rate. */
  static final int SAMPLE_RATE = 24_000;

  private final GeminiVoiceMap voiceMap = new GeminiVoiceMap();

  String modelId() {
    return MODEL_ID;
  }

  /** The {@code response_format} requested of OpenRouter's speech endpoint. */
  String responseFormat() {
    return RESPONSE_FORMAT;
  }

  /** The emotions this model can render; anything outside is downgraded to neutral upstream. */
  EnumSet<Emotion> supportedEmotions() {
    return EnumSet.copyOf(GeminiEmotionStyle.SUPPORTED);
  }

  /** The concrete Gemini voice name for a backend-neutral {@link VoiceSpec}. */
  String voiceFor(VoiceSpec voice) {
    return voiceMap.voiceFor(voice);
  }

  /** Applies the model's emotion styling (an inline style tag) to the spoken text. */
  String styleInput(String text, Emotion emotion) {
    return GeminiEmotionStyle.apply(text, emotion);
  }

  /** Decodes the model's audio response bytes into {@link Pcm}, or {@code null} if undecodable. */
  Pcm decodeResponse(byte[] bytes) {
    return RawPcmDecoder.decode(bytes, SAMPLE_RATE);
  }

  /**
   * The sample rate (Hz) of the decoded PCM. The streaming path decodes the body incrementally and
   * so must be told the rate out of band, since the raw stream carries no header.
   */
  int sampleRate() {
    return SAMPLE_RATE;
  }
}
