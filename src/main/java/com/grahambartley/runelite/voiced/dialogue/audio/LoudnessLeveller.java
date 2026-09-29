package com.grahambartley.runelite.voiced.dialogue.audio;

public final class LoudnessLeveller {

  static final double TARGET_SPEECH_DBFS = -18.0;
  static final double PEAK_CEILING_DBFS = -1.0;
  static final double MAX_BOOST_DB = 12.0;
  static final double SILENCE_GATE_DBFS = -50.0;
  static final int WINDOW_MS = 20;

  private static final double TARGET_RMS = dbToAmplitude(TARGET_SPEECH_DBFS);
  private static final double PEAK_CEILING = dbToAmplitude(PEAK_CEILING_DBFS);
  private static final double MAX_GAIN = dbToAmplitude(MAX_BOOST_DB);
  private static final double GATE_MEAN_SQUARE =
      dbToAmplitude(SILENCE_GATE_DBFS) * dbToAmplitude(SILENCE_GATE_DBFS);

  private LoudnessLeveller() {}

  public static float gainFor(float[] samples, int sampleRate) {
    int window = Math.max(1, sampleRate * WINDOW_MS / 1000);
    double speechEnergy = 0;
    int speechWindows = 0;
    float peak = 0f;
    for (int start = 0; start < samples.length; start += window) {
      int end = Math.min(samples.length, start + window);
      double energy = 0;
      for (int i = start; i < end; i++) {
        float s = samples[i];
        energy += (double) s * s;
        peak = Math.max(peak, Math.abs(s));
      }
      double meanSquare = energy / (end - start);
      if (meanSquare > GATE_MEAN_SQUARE) {
        speechEnergy += meanSquare;
        speechWindows++;
      }
    }
    if (speechWindows == 0) {
      return 1f;
    }
    double speechRms = Math.sqrt(speechEnergy / speechWindows);
    double gain = Math.min(TARGET_RMS / speechRms, MAX_GAIN);
    return (float) Math.min(gain, PEAK_CEILING / peak);
  }

  public static float[] scaled(float[] samples, float gain) {
    if (gain == 1f) {
      return samples;
    }
    float[] out = new float[samples.length];
    for (int i = 0; i < samples.length; i++) {
      out[i] = Math.max(-1f, Math.min(1f, samples[i] * gain));
    }
    return out;
  }

  public static float[] levelled(float[] samples, int sampleRate) {
    return scaled(samples, gainFor(samples, sampleRate));
  }

  private static double dbToAmplitude(double db) {
    return Math.pow(10.0, db / 20.0);
  }
}
