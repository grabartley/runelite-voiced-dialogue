package com.grahambartley.tts;

/** Mono PCM: float samples in [-1, 1] at the given sample rate (24 kHz for the cloud backend). */
public final class Pcm {
  private final float[] samples;
  private final int sampleRate;

  public Pcm(float[] samples, int sampleRate) {
    this.samples = samples;
    this.sampleRate = sampleRate;
  }

  public float[] getSamples() {
    return samples;
  }

  public int getSampleRate() {
    return sampleRate;
  }
}
