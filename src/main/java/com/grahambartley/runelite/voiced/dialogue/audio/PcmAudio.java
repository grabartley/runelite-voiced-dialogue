package com.grahambartley.runelite.voiced.dialogue.audio;

import javax.sound.sampled.AudioFormat;

public final class PcmAudio {

  private PcmAudio() {}

  public static byte[] toPcm16LE(float[] samples, int from, int to, float gain) {
    byte[] pcm = new byte[(to - from) * 2];
    for (int i = from; i < to; i++) {
      float clamped = Math.max(-1f, Math.min(1f, samples[i] * gain));
      int s = Math.round(clamped * 32767f);
      pcm[2 * (i - from)] = (byte) (s & 0xff);
      pcm[2 * (i - from) + 1] = (byte) ((s >> 8) & 0xff);
    }
    return pcm;
  }

  public static AudioFormat format(int sampleRate) {
    return new AudioFormat(sampleRate, 16, 1, true, false);
  }
}
