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
    for (int start = 0; start < samples.length; start += window) {
      int end = Math.min(samples.length, start + window);
      double energy = 0;
      for (int i = start; i < end; i++) {
        energy += (double) samples[i] * samples[i];
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
    return withinCeiling(samples, (float) Math.min(TARGET_RMS / speechRms, MAX_GAIN));
  }

  public static float withinCeiling(float[] samples, float gain) {
    float peak = 0f;
    for (float s : samples) {
      peak = Math.max(peak, Math.abs(s));
    }
    return peak == 0f ? gain : (float) Math.min(gain, PEAK_CEILING / peak);
  }

  private static double dbToAmplitude(double db) {
    return Math.pow(10.0, db / 20.0);
  }
}
