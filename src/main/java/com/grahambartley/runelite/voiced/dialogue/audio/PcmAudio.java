package com.grahambartley.runelite.voiced.dialogue.audio;

import javax.sound.sampled.AudioFormat;

public final class PcmAudio {

  private PcmAudio() {}

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

  public static AudioFormat format(int sampleRate) {
    return new AudioFormat(sampleRate, 16, 1, true, false);
  }
}
