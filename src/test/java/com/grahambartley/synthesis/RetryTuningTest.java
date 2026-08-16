package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import org.junit.Test;

/** Each provider's ceiling reflects how it delivers audio, rather than one shared worst case. */
public class RetryTuningTest {

  @Test
  public void aiStudioReleasesAStalledCallSoonerThanOpenRouter() {
    assertTrue(
        "a streaming provider needs only its own generation time, not OpenRouter's whole-clip wait",
        RetryTuning.googleAiStudio().callTimeout.compareTo(RetryTuning.openRouter().callTimeout)
            < 0);
  }

  @Test
  public void aiStudioClearsItsLongestMeasuredLine() {
    assertTrue(
        "a 500 character line completes in roughly 14s, so the ceiling leaves real headroom",
        RetryTuning.googleAiStudio().callTimeout.compareTo(Duration.ofSeconds(30)) > 0);
  }

  @Test
  public void readBudgetMatchesTheCeilingForBothProviders() {
    for (RetryTuning tuning :
        new RetryTuning[] {RetryTuning.openRouter(), RetryTuning.googleAiStudio()}) {
      assertEquals(
          "the whole wait for a line can arrive as a single read, so a shorter per-read budget"
              + " would kill a line that was about to succeed",
          tuning.callTimeout,
          tuning.readTimeout);
    }
  }

  @Test
  public void connectStaysShortForBothProviders() {
    assertEquals(
        RetryTuning.openRouter().connectTimeout, RetryTuning.googleAiStudio().connectTimeout);
    assertTrue(
        "an unreachable host should fail the line fast rather than hang",
        RetryTuning.openRouter().connectTimeout.compareTo(Duration.ofSeconds(5)) < 0);
  }
}
