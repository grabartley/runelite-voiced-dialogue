package com.grahambartley.runelite.voiced.dialogue.audio;

public final class PcmCompleteness {

  private static final float SILENCE_FLOOR = 0.02f;

  private static final int MIN_TRAILING_SILENCE_MS = 120;

  private static final int MIN_JUDGED_MS = 500;

  private PcmCompleteness() {}

  public static boolean isTruncated(Pcm pcm, double speedRatio) {
    int rate = pcm.getSampleRate();
    if (rate <= 0) {
      return false;
    }
    float[] samples = pcm.getSamples();
    if (samples.length < msToSamples(MIN_JUDGED_MS, rate)) {
      return false;
    }
    double ratio = speedRatio > 0 ? speedRatio : 1.0;
    int needed = Math.max(1, (int) Math.round(msToSamples(MIN_TRAILING_SILENCE_MS, rate) / ratio));
    int quiet = 0;
    for (int i = samples.length - 1; i >= 0; i--) {
      if (Math.abs(samples[i]) >= SILENCE_FLOOR) {
        break;
      }
      if (++quiet >= needed) {
        return false;
      }
    }
    return true;
  }

  private static int msToSamples(int ms, int rate) {
    return Math.round(ms / 1000f * rate);
  }
}
