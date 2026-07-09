package com.grahambartley.synthesis;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.grahambartley.tts.Pcm;
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
    // 2 s of speech ending on 200 ms of silence: well past the 120 ms a complete line carries.
    assertFalse(PcmCompleteness.isTruncated(clip(48_000, 4_800), 1.0));
  }

  @Test
  public void aLineThatEndsMidSignalIsTruncated() {
    // 2 s of full-amplitude audio with no trailing quiet: the cut-off case.
    assertTrue(PcmCompleteness.isTruncated(clip(48_000, 0), 1.0));
  }

  @Test
  public void aLineWithTooLittleTrailingSilenceIsTruncated() {
    // Only 40 ms of trailing silence, under the 120 ms a clean ending carries.
    assertTrue(PcmCompleteness.isTruncated(clip(48_000, 960), 1.0));
  }

  @Test
  public void aClipTooShortToJudgeIsLeftAlone() {
    // A 100 ms reply is too short to carry a reliable trailing-silence tell, so never flagged.
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
    // 62.5 ms of trailing silence: short of the 120 ms bar at default pace, but a complete line
    // spoken at 2x pace only needs ~60 ms, since the natural release is time-compressed with the
    // speech. The same buffer must flag at 1.0 and not flag at 2.0 (the reported false positive).
    assertTrue(
        "short trailing silence is a cut at default pace",
        PcmCompleteness.isTruncated(clip(48_000, 1_500), 1.0));
    assertFalse(
        "the same buffer is complete once the window is scaled for 2x pace",
        PcmCompleteness.isTruncated(clip(48_000, 1_500), 2.0));
  }

  @Test
  public void aSlowLineRequiresProportionallyMoreTrailingSilence() {
    // 200 ms of trailing silence clears the bar at default pace, but at 0.5x pace a complete line's
    // release stretches to ~240 ms, so the same buffer is now short and reads as truncated.
    assertFalse(
        "200 ms of quiet is a clean ending at default pace",
        PcmCompleteness.isTruncated(clip(48_000, 4_800), 1.0));
    assertTrue(
        "the same buffer is short of the pace-scaled window at 0.5x",
        PcmCompleteness.isTruncated(clip(48_000, 4_800), 0.5));
  }

  @Test
  public void aNonPositiveSpeedRatioFallsBackToDefaultPace() {
    // A zero/negative ratio must not divide the window to nothing; it behaves like 1.0.
    assertTrue(PcmCompleteness.isTruncated(clip(48_000, 1_500), 0.0));
    assertFalse(PcmCompleteness.isTruncated(clip(48_000, 4_800), -1.0));
  }
}
