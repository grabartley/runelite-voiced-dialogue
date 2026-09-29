package com.grahambartley.runelite.voiced.dialogue.audio;

import static com.grahambartley.runelite.voiced.dialogue.audio.TestPcm.peak;
import static com.grahambartley.runelite.voiced.dialogue.audio.TestPcm.rms;
import static com.grahambartley.runelite.voiced.dialogue.audio.TestPcm.sine;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LoudnessLevellerTest {

  private static final int RATE = 24_000;
  private static final int ONE_SECOND = RATE;
  private static final float TARGET_RMS = (float) Math.pow(10, -18.0 / 20);
  private static final float CEILING = (float) Math.pow(10, -1.0 / 20);
  private static final float SINE_CREST = (float) Math.sqrt(2);
  private static final float DB_TOLERANCE = 0.1f;

  private static float db(float amplitude) {
    return (float) (20 * Math.log10(amplitude));
  }

  private static float amplitudeForRms(float rms) {
    return rms * SINE_CREST;
  }

  @Test
  public void aQuietClipIsRaisedToTheTarget() {
    float[] quiet = sine(amplitudeForRms(TARGET_RMS / 3), ONE_SECOND);

    float[] out = LoudnessLeveller.levelled(quiet, RATE);

    assertEquals(db(TARGET_RMS), db(rms(out)), DB_TOLERANCE);
  }

  @Test
  public void aLoudClipIsLoweredToTheTarget() {
    float[] loud = sine(amplitudeForRms(TARGET_RMS * 3), ONE_SECOND);

    float[] out = LoudnessLeveller.levelled(loud, RATE);

    assertEquals(db(TARGET_RMS), db(rms(out)), DB_TOLERANCE);
  }

  @Test
  public void aClipAlreadyAtTheTargetIsUntouched() {
    float[] atTarget = sine(amplitudeForRms(TARGET_RMS), ONE_SECOND);

    assertEquals(1f, LoudnessLeveller.gainFor(atTarget, RATE), 1e-3f);
  }

  @Test
  public void silenceIsLeftAlone() {
    float[] silence = new float[ONE_SECOND];

    assertEquals(1f, LoudnessLeveller.gainFor(silence, RATE), 0f);
    assertSame(silence, LoudnessLeveller.levelled(silence, RATE));
  }

  @Test
  public void noiseBelowTheSilenceGateIsNotBoosted() {
    float[] hiss = sine(1e-4f, ONE_SECOND);

    assertEquals(1f, LoudnessLeveller.gainFor(hiss, RATE), 0f);
  }

  @Test
  public void pausesBetweenWordsDoNotCountTowardsTheLevel() {
    float amplitude = amplitudeForRms(TARGET_RMS);
    float[] speechWithPause = new float[ONE_SECOND * 2];
    System.arraycopy(sine(amplitude, ONE_SECOND), 0, speechWithPause, 0, ONE_SECOND);

    assertEquals(1f, LoudnessLeveller.gainFor(speechWithPause, RATE), 1e-3f);
  }

  @Test
  public void theBoostIsCappedSoAWhisperIsNotBlownUp() {
    float[] whisper = sine(amplitudeForRms(TARGET_RMS / 10), ONE_SECOND);

    float gain = LoudnessLeveller.gainFor(whisper, RATE);

    assertEquals(12f, db(gain), DB_TOLERANCE);
  }

  @Test
  public void aSpikyClipIsLimitedByItsPeakNotItsLevel() {
    float[] spiky = sine(amplitudeForRms(TARGET_RMS / 4), ONE_SECOND);
    spiky[100] = 0.5f;

    float[] out = LoudnessLeveller.levelled(spiky, RATE);

    assertEquals(CEILING, peak(out), 1e-4f);
    assertTrue(rms(out) < TARGET_RMS);
  }

  @Test
  public void noOutputSampleExceedsFullScale() {
    float[] clipped = sine(1f, ONE_SECOND);
    clipped[7] = 1.5f;
    clipped[8] = -1.5f;

    assertTrue(peak(LoudnessLeveller.levelled(clipped, RATE)) <= CEILING + 1e-6f);
    assertTrue(peak(LoudnessLeveller.scaled(clipped, 4f)) <= 1f);
  }

  @Test
  public void aUnitGainReturnsTheSamplesUnchanged() {
    float[] samples = {0.1f, -0.2f};

    assertSame(samples, LoudnessLeveller.scaled(samples, 1f));
  }

  @Test
  public void scalingMultipliesEverySample() {
    assertArrayEquals(
        new float[] {0.2f, -0.4f}, LoudnessLeveller.scaled(new float[] {0.1f, -0.2f}, 2f), 1e-6f);
  }

  @Test
  public void aClipShorterThanOneWindowIsStillMeasured() {
    float[] blip = {0.5f, -0.5f, 0.5f};

    assertTrue(LoudnessLeveller.gainFor(blip, RATE) < 1f);
  }
}
