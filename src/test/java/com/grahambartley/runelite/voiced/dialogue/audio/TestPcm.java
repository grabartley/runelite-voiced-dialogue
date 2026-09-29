package com.grahambartley.runelite.voiced.dialogue.audio;

import java.io.ByteArrayOutputStream;

public final class TestPcm {

  private static final int SINE_PERIOD = 20;

  private TestPcm() {}

  public static byte[] raw(short[] samples) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (short s : samples) {
      out.write(s & 0xff);
      out.write((s >> 8) & 0xff);
    }
    return out.toByteArray();
  }

  public static float[] sine(float amplitude, int samples) {
    float[] out = new float[samples];
    for (int i = 0; i < samples; i++) {
      out[i] = (float) (amplitude * Math.sin(2 * Math.PI * i / SINE_PERIOD));
    }
    return out;
  }

  public static float rms(float[] samples) {
    double energy = 0;
    for (float s : samples) {
      energy += (double) s * s;
    }
    return (float) Math.sqrt(energy / samples.length);
  }

  public static float peak(float[] samples) {
    float peak = 0f;
    for (float s : samples) {
      peak = Math.max(peak, Math.abs(s));
    }
    return peak;
  }

  public static int peak16(byte[] pcm) {
    int peak = 0;
    for (int i = 0; i + 1 < pcm.length; i += 2) {
      short s = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
      peak = Math.max(peak, Math.abs(s));
    }
    return peak;
  }
}
