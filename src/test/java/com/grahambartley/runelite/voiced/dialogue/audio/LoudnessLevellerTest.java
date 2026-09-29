package com.grahambartley.runelite.voiced.dialogue.audio;

import static com.grahambartley.runelite.voiced.dialogue.audio.TestPcm.peak;
import static com.grahambartley.runelite.voiced.dialogue.audio.TestPcm.rms;
import static com.grahambartley.runelite.voiced.dialogue.audio.TestPcm.sine;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LoudnessLevellerTest {

  private static final int RATE = 24_000;
  private static final int ONE_SECOND = RATE;
  private static final float FULL_VOLUME_RMS = (float) Math.pow(10, -18.0 / 20);
  private static final float CEILING = (float) Math.pow(10, -1.0 / 20);
  private static final float SINE_CREST = (float) Math.sqrt(2);
  private static final float DB_TOLERANCE = 0.1f;

  private static float db(float amplitude) {
    return (float) (20 * Math.log10(amplitude));
  }

  private static float amplitudeForRms(float rms) {
    return rms * SINE_CREST;
  }

  private static float[] played(float[] samples, int volumePercent) {
    float gain = LoudnessLeveller.measure(samples, RATE).gainAt(volumePercent);
    float[] out = new float[samples.length];
    for (int i = 0; i < samples.length; i++) {
      out[i] = samples[i] * gain;
    }
    return out;
  }

  @Test
  public void aQuietClipIsRaisedToTheFullVolumeLevel() {
    float[] quiet = sine(amplitudeForRms(FULL_VOLUME_RMS / 3), ONE_SECOND);

    assertEquals(db(FULL_VOLUME_RMS), db(rms(played(quiet, 100))), DB_TOLERANCE);
  }

  @Test
  public void aLoudClipIsLoweredToTheFullVolumeLevel() {
    float[] loud = sine(amplitudeForRms(FULL_VOLUME_RMS * 3), ONE_SECOND);

    assertEquals(db(FULL_VOLUME_RMS), db(rms(played(loud, 100))), DB_TOLERANCE);
  }

  @Test
  public void aClipAlreadyAtTheLevelIsUntouchedAtFullVolume() {
    float[] atLevel = sine(amplitudeForRms(FULL_VOLUME_RMS), ONE_SECOND);

    assertEquals(1f, LoudnessLeveller.measure(atLevel, RATE).gainAt(100), 1e-3f);
  }

  @Test
  public void theVolumeSetsTheLevelEveryClipLandsOn() {
    float[] quiet = sine(amplitudeForRms(FULL_VOLUME_RMS / 3), ONE_SECOND);
    float[] loud = sine(amplitudeForRms(FULL_VOLUME_RMS * 3), ONE_SECOND);
    float expected = db(FULL_VOLUME_RMS * 0.5f);

    assertEquals(expected, db(rms(played(quiet, 50))), DB_TOLERANCE);
    assertEquals(expected, db(rms(played(loud, 50))), DB_TOLERANCE);
  }

  @Test
  public void zeroVolumeMutes() {
    float[] speech = sine(0.2f, ONE_SECOND);

    assertEquals(0f, LoudnessLeveller.measure(speech, RATE).gainAt(0), 0f);
  }

  @Test
  public void volumeOutsideTheRangeIsClamped() {
    LoudnessLeveller leveller =
        LoudnessLeveller.measure(sine(amplitudeForRms(FULL_VOLUME_RMS), ONE_SECOND), RATE);

    assertEquals(leveller.gainAt(100), leveller.gainAt(250), 0f);
    assertEquals(0f, leveller.gainAt(-5), 0f);
  }

  @Test
  public void silenceIsLeftAlone() {
    LoudnessLeveller leveller = LoudnessLeveller.measure(new float[ONE_SECOND], RATE);

    assertEquals(1f, leveller.gainAt(100), 0f);
    assertEquals(0.2f, leveller.gainAt(20), 1e-6f);
  }

  @Test
  public void noiseBelowTheSilenceGateIsNotBoosted() {
    assertEquals(1f, LoudnessLeveller.measure(sine(1e-4f, ONE_SECOND), RATE).gainAt(100), 0f);
  }

  @Test
  public void pausesBetweenWordsDoNotCountTowardsTheLevel() {
    float[] speechWithPause = new float[ONE_SECOND * 2];
    float[] speech = sine(amplitudeForRms(FULL_VOLUME_RMS), ONE_SECOND);
    System.arraycopy(speech, 0, speechWithPause, 0, ONE_SECOND);

    assertEquals(1f, LoudnessLeveller.measure(speechWithPause, RATE).gainAt(100), 1e-3f);
  }

  @Test
  public void theBoostIsCappedSoAWhisperIsNotBlownUp() {
    float[] whisper = sine(amplitudeForRms(FULL_VOLUME_RMS / 10), ONE_SECOND);

    assertEquals(12f, db(LoudnessLeveller.measure(whisper, RATE).gainAt(100)), DB_TOLERANCE);
  }

  @Test
  public void aSpikyClipIsLimitedByItsPeakAtFullVolume() {
    float[] spiky = sine(amplitudeForRms(FULL_VOLUME_RMS / 3), ONE_SECOND);
    spiky[100] = 0.5f;

    float[] out = played(spiky, 100);

    assertEquals(CEILING, peak(out), 1e-4f);
    assertTrue(rms(out) < FULL_VOLUME_RMS);
  }

  @Test
  public void aSpikyClipReachesTheLevelAtEverydayVolume() {
    float[] spiky = sine(amplitudeForRms(FULL_VOLUME_RMS / 3), ONE_SECOND);
    spiky[100] = 0.5f;

    assertEquals(db(FULL_VOLUME_RMS * 0.2f), db(rms(played(spiky, 20))), DB_TOLERANCE);
  }

  @Test
  public void noPlayedSampleExceedsTheCeiling() {
    float[] clipped = sine(1f, ONE_SECOND);
    clipped[7] = 1.5f;
    clipped[8] = -1.5f;

    assertTrue(peak(played(clipped, 100)) <= CEILING + 1e-6f);
  }

  @Test
  public void aLouderLaterChunkLowersTheGain() {
    LoudnessLeveller leveller =
        LoudnessLeveller.measure(sine(amplitudeForRms(FULL_VOLUME_RMS / 3), ONE_SECOND), RATE);
    float before = leveller.gainAt(100);

    leveller.include(new float[] {0.5f, -0.5f});

    assertEquals(CEILING / 0.5f, leveller.gainAt(100), 1e-5f);
    assertTrue(leveller.gainAt(100) < before);
  }

  @Test
  public void aQuieterLaterChunkKeepsTheGain() {
    LoudnessLeveller leveller =
        LoudnessLeveller.measure(sine(amplitudeForRms(FULL_VOLUME_RMS), ONE_SECOND), RATE);
    float before = leveller.gainAt(100);

    leveller.include(new float[] {0.01f, -0.01f});

    assertEquals(before, leveller.gainAt(100), 0f);
  }

  @Test
  public void aClipShorterThanOneWindowIsStillMeasured() {
    assertTrue(LoudnessLeveller.measure(new float[] {0.5f, -0.5f, 0.5f}, RATE).gainAt(100) < 1f);
  }

  private static LoudnessLeveller streamedFor(float amplitude, double seconds) {
    LoudnessLeveller leveller = LoudnessLeveller.streaming(RATE);
    int chunk = RATE / 20;
    for (int i = 0; i < Math.round(seconds * 20); i++) {
      leveller.include(sine(amplitude, chunk));
    }
    return leveller;
  }

  @Test
  public void aStreamedLineStartsAtItsRawLevel() {
    assertEquals(1f, LoudnessLeveller.streaming(RATE).gainAt(100), 0f);
  }

  @Test
  public void aStreamedLineSlidesAtMostThreeDecibelsPerSecond() {
    float quiet = amplitudeForRms(FULL_VOLUME_RMS / 4);

    assertEquals(3f, db(streamedFor(quiet, 1).gainAt(100)), DB_TOLERANCE);
    assertEquals(6f, db(streamedFor(quiet, 2).gainAt(100)), DB_TOLERANCE);
  }

  @Test
  public void aStreamedLineSettlesOnTheWholeLineLevel() {
    float[] line = sine(amplitudeForRms(FULL_VOLUME_RMS / 2), ONE_SECOND * 3);
    float whole = LoudnessLeveller.measure(line, RATE).gainAt(100);

    assertEquals(
        db(whole),
        db(streamedFor(amplitudeForRms(FULL_VOLUME_RMS / 2), 3).gainAt(100)),
        DB_TOLERANCE);
  }

  @Test
  public void aStreamedLineLocksAfterThreeSecondsOfSpeech() {
    float quiet = amplitudeForRms(FULL_VOLUME_RMS / 10);
    LoudnessLeveller leveller = streamedFor(quiet, 3);
    float locked = leveller.gainAt(100);

    leveller.include(sine(quiet, ONE_SECOND * 2));

    assertEquals(9f, db(locked), DB_TOLERANCE);
    assertEquals(locked, leveller.gainAt(100), 0f);
  }

  @Test
  public void silenceNeitherMovesNorLocksAStreamedLine() {
    LoudnessLeveller leveller = LoudnessLeveller.streaming(RATE);
    leveller.include(new float[ONE_SECOND * 5]);

    assertEquals(1f, leveller.gainAt(100), 0f);
    leveller.include(sine(amplitudeForRms(FULL_VOLUME_RMS / 4), ONE_SECOND));
    assertTrue(leveller.gainAt(100) > 1f);
  }

  @Test
  public void theVolumeScalesAStreamedLineThroughoutTheSlide() {
    float quiet = amplitudeForRms(FULL_VOLUME_RMS / 4);
    for (double seconds : new double[] {0.1, 1, 2, 4}) {
      LoudnessLeveller leveller = streamedFor(quiet, seconds);
      assertEquals(leveller.gainAt(100) * 0.2f, leveller.gainAt(20), 1e-6f);
      assertEquals(0f, leveller.gainAt(0), 0f);
    }
  }
}
