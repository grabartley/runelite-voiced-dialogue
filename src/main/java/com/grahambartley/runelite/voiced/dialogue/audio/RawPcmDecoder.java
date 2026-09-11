package com.grahambartley.runelite.voiced.dialogue.audio;

public final class RawPcmDecoder {

  private RawPcmDecoder() {}

  public static Pcm decode(byte[] pcm, int sampleRate) {
    if (pcm == null || pcm.length < 2 || sampleRate <= 0) {
      return null;
    }
    if ((pcm.length & 1) != 0) {
      return null;
    }
    int sampleCount = pcm.length / 2;
    float[] samples = new float[sampleCount];
    for (int i = 0; i < sampleCount; i++) {
      int lo = pcm[2 * i] & 0xff;
      int hi = pcm[2 * i + 1];
      short s = (short) ((hi << 8) | lo);
      samples[i] = s / 32768f;
    }
    return new Pcm(samples, sampleRate);
  }
}
