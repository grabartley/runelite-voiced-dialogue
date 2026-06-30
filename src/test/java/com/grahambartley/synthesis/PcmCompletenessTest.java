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
    assertFalse(PcmCompleteness.isTruncated(clip(48_000, 4_800)));
  }

  @Test
  public void aLineThatEndsMidSignalIsTruncated() {
    // 2 s of full-amplitude audio with no trailing quiet: the cut-off case.
    assertTrue(PcmCompleteness.isTruncated(clip(48_000, 0)));
  }

  @Test
  public void aLineWithTooLittleTrailingSilenceIsTruncated() {
    // Only 40 ms of trailing silence, under the 120 ms a clean ending carries.
    assertTrue(PcmCompleteness.isTruncated(clip(48_000, 960)));
  }

  @Test
  public void aClipTooShortToJudgeIsLeftAlone() {
    // A 100 ms reply is too short to carry a reliable trailing-silence tell, so never flagged.
    assertFalse(PcmCompleteness.isTruncated(clip(2_400, 0)));
  }

  @Test
  public void emptyAudioIsNotFlagged() {
    assertFalse(PcmCompleteness.isTruncated(new Pcm(new float[0], RATE)));
  }

  @Test
  public void nonPositiveRateIsNotFlagged() {
    assertFalse(PcmCompleteness.isTruncated(new Pcm(new float[48_000], 0)));
  }
}
