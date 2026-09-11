package com.grahambartley.runelite.voiced.dialogue.audio;

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
