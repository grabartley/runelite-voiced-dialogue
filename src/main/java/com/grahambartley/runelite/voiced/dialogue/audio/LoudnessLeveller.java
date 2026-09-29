package com.grahambartley.runelite.voiced.dialogue.audio;

public final class LoudnessLeveller {

  private static final double FULL_VOLUME_SPEECH_DBFS = -18.0;
  private static final double PEAK_CEILING_DBFS = -1.0;
  private static final double MAX_BOOST_DB = 12.0;
  private static final double SILENCE_GATE_DBFS = -50.0;
  private static final int WINDOW_MS = 20;
  private static final int FULL_VOLUME_PERCENT = 100;
  private static final double STREAMED_SLEW_DB_PER_SECOND = 3.0;
  private static final double STREAMED_LOCK_SPEECH_SECONDS = 3.0;

  private static final double FULL_VOLUME_RMS = dbToAmplitude(FULL_VOLUME_SPEECH_DBFS);
  private static final double PEAK_CEILING = dbToAmplitude(PEAK_CEILING_DBFS);
  private static final double MAX_GAIN = dbToAmplitude(MAX_BOOST_DB);
  private static final double GATE_MEAN_SQUARE =
      dbToAmplitude(SILENCE_GATE_DBFS) * dbToAmplitude(SILENCE_GATE_DBFS);

  private final int sampleRate;
  private final SpeechEnergy heard;
  private float fullVolumeGain;
  private float peak;
  private boolean locked;

  private LoudnessLeveller(int sampleRate, SpeechEnergy heard, float peak, boolean locked) {
    this.sampleRate = sampleRate;
    this.heard = heard;
    this.fullVolumeGain = locked ? heard.fullVolumeGain() : 1f;
    this.peak = peak;
    this.locked = locked;
  }

  public static LoudnessLeveller measure(float[] samples, int sampleRate) {
    SpeechEnergy speech = new SpeechEnergy();
    speech.add(samples, sampleRate);
    return new LoudnessLeveller(sampleRate, speech, peakOf(samples), true);
  }

  public static LoudnessLeveller streaming(int sampleRate) {
    return new LoudnessLeveller(sampleRate, new SpeechEnergy(), 0f, false);
  }

  public void include(float[] chunk) {
    peak = Math.max(peak, peakOf(chunk));
    if (locked) {
      return;
    }
    heard.add(chunk, sampleRate);
    double seconds = (double) chunk.length / sampleRate;
    double wantedDb = amplitudeToDb(heard.fullVolumeGain());
    double currentDb = amplitudeToDb(fullVolumeGain);
    double stepDb = STREAMED_SLEW_DB_PER_SECOND * seconds;
    double nextDb = currentDb + Math.max(-stepDb, Math.min(stepDb, wantedDb - currentDb));
    fullVolumeGain = (float) dbToAmplitude(nextDb);
    locked = heard.seconds(sampleRate) >= STREAMED_LOCK_SPEECH_SECONDS;
  }

  public float gainAt(int volumePercent) {
    int volume = Math.max(0, Math.min(FULL_VOLUME_PERCENT, volumePercent));
    float gain = fullVolumeGain * volume / FULL_VOLUME_PERCENT;
    return peak == 0f ? gain : (float) Math.min(gain, PEAK_CEILING / peak);
  }

  private static float peakOf(float[] samples) {
    float peak = 0f;
    for (float s : samples) {
      peak = Math.max(peak, Math.abs(s));
    }
    return peak;
  }

  private static double dbToAmplitude(double db) {
    return Math.pow(10.0, db / 20.0);
  }

  private static double amplitudeToDb(double amplitude) {
    return 20.0 * Math.log10(amplitude);
  }

  private static final class SpeechEnergy {
    private double energy;
    private long samples;

    void add(float[] chunk, int sampleRate) {
      int window = Math.max(1, sampleRate * WINDOW_MS / 1000);
      for (int start = 0; start < chunk.length; start += window) {
        int end = Math.min(chunk.length, start + window);
        double windowEnergy = 0;
        for (int i = start; i < end; i++) {
          windowEnergy += (double) chunk[i] * chunk[i];
        }
        if (windowEnergy / (end - start) > GATE_MEAN_SQUARE) {
          energy += windowEnergy;
          samples += end - start;
        }
      }
    }

    double seconds(int sampleRate) {
      return (double) samples / sampleRate;
    }

    float fullVolumeGain() {
      if (samples == 0) {
        return 1f;
      }
      return (float) Math.min(FULL_VOLUME_RMS / Math.sqrt(energy / samples), MAX_GAIN);
    }
  }
}
