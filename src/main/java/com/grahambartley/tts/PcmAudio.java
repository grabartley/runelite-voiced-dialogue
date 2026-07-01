package com.grahambartley.tts;

import javax.sound.sampled.AudioFormat;

/**
 * Pure conversion helpers for synthesized audio output.
 *
 * <p>The decoded {@link Pcm} carries mono {@code float} samples in the range [-1, 1]. {@code
 * javax.sound} needs signed 16-bit little-endian PCM, so these helpers bridge the two without
 * touching any native code, which keeps them straightforward to unit test.
 */
public final class PcmAudio {

  private PcmAudio() {}

  /** Converts mono float samples in [-1, 1] to signed 16-bit little-endian PCM bytes. */
  public static byte[] toPcm16LE(float[] samples) {
    byte[] pcm = new byte[samples.length * 2];
    for (int i = 0; i < samples.length; i++) {
      float clamped = Math.max(-1f, Math.min(1f, samples[i]));
      int s = Math.round(clamped * 32767f);
      pcm[2 * i] = (byte) (s & 0xff);
      pcm[2 * i + 1] = (byte) ((s >> 8) & 0xff);
    }
    return pcm;
  }

  /** Audio format for the PCM at the given sample rate: 16-bit, mono, signed, little-endian. */
  public static AudioFormat format(int sampleRate) {
    return new AudioFormat(sampleRate, 16, 1, true, false);
  }
}
