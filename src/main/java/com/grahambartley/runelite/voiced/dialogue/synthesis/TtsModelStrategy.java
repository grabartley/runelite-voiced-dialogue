package com.grahambartley.runelite.voiced.dialogue.synthesis;

import com.grahambartley.runelite.voiced.dialogue.tts.Pcm;
import java.util.EnumSet;

/**
 * The model-specific half of cloud synthesis, so {@link OpenRouterTtsBackend} can drive any
 * OpenRouter TTS model without baking one model's voice catalog, request shape, emotion handling,
 * or audio format into its HTTP/translation/cache/retry machinery. {@link GeminiTtsModel} is the
 * first (and currently only) implementation; a different OpenRouter speech model is added by
 * implementing this interface, not by editing the backend.
 */
interface TtsModelStrategy {

  /** The OpenRouter model id sent as the {@code model} field. */
  String modelId();

  /** The {@code response_format} requested (e.g. {@code "pcm"}). */
  String responseFormat();

  /** The emotions this model can render; anything outside is downgraded to neutral upstream. */
  EnumSet<Emotion> supportedEmotions();

  /** The concrete model voice name for a backend-neutral {@link VoiceSpec}. */
  String voiceFor(VoiceSpec voice);

  /** Applies the model's emotion styling to the spoken text (e.g. an inline style tag). */
  String styleInput(String text, Emotion emotion);

  /** Decodes the model's audio response bytes into {@link Pcm}, or {@code null} if undecodable. */
  Pcm decodeResponse(byte[] bytes);

  /**
   * The sample rate (Hz) of the decoded PCM. The streaming path decodes the body incrementally and
   * so must be told the rate out of band, since the raw stream carries no header.
   */
  int sampleRate();
}
