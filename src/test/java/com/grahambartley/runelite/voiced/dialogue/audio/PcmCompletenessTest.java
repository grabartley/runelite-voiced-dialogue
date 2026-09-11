package com.grahambartley.runelite.voiced.dialogue.audio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class PcmCompletenessTest {

  private static final int RATE = 24_000;

  private static Pcm clip(int speechSamples, int trailingSilenceSamples) {
    float[] s = new float[speechSamples + trailingSilenceSamples];
    Arrays.fill(s, 0, speechSamples, 0.5f);
    return new Pcm(s, RATE);
  }

  @Test
  public void aLineThatReleasesIntoTrailingSilenceIsComplete() {
    assertFalse(PcmCompleteness.isTruncated(clip(48_000, 4_800), 1.0));
  }

  @Test
  public void aLineThatEndsMidSignalIsTruncated() {
    assertTrue(PcmCompleteness.isTruncated(clip(48_000, 0), 1.0));
  }

  @Test
  public void aLineWithTooLittleTrailingSilenceIsTruncated() {
    assertTrue(PcmCompleteness.isTruncated(clip(48_000, 960), 1.0));
  }

  @Test
  public void aClipTooShortToJudgeIsLeftAlone() {
    assertFalse(PcmCompleteness.isTruncated(clip(2_400, 0), 1.0));
  }

  @Test
  public void emptyAudioIsNotFlagged() {
    assertFalse(PcmCompleteness.isTruncated(new Pcm(new float[0], RATE), 1.0));
  }

  @Test
  public void nonPositiveRateIsNotFlagged() {
    assertFalse(PcmCompleteness.isTruncated(new Pcm(new float[48_000], 0), 1.0));
  }

  @Test
  public void aFastLineIsNotFlaggedWhenItsTrailingSilenceScalesWithPace() {
    assertTrue(
        "short trailing silence is a cut at default pace",
        PcmCompleteness.isTruncated(clip(48_000, 1_500), 1.0));
    assertFalse(
        "the same buffer is complete once the window is scaled for 2x pace",
        PcmCompleteness.isTruncated(clip(48_000, 1_500), 2.0));
  }

  @Test
  public void aSlowLineRequiresProportionallyMoreTrailingSilence() {
    assertFalse(
        "200 ms of quiet is a clean ending at default pace",
        PcmCompleteness.isTruncated(clip(48_000, 4_800), 1.0));
    assertTrue(
        "the same buffer is short of the pace-scaled window at 0.5x",
        PcmCompleteness.isTruncated(clip(48_000, 4_800), 0.5));
  }

  @Test
  public void aNonPositiveSpeedRatioFallsBackToDefaultPace() {
    assertTrue(PcmCompleteness.isTruncated(clip(48_000, 1_500), 0.0));
    assertFalse(PcmCompleteness.isTruncated(clip(48_000, 4_800), -1.0));
  }
}
