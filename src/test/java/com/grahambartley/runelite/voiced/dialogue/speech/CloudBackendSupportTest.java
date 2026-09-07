package com.grahambartley.runelite.voiced.dialogue.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The shared stateful backend plumbing: notice guarding, the speaking-pace clamp, and the retry
 * backoff wait. The retry loops and failure logging that use it are pinned by each backend's own
 * tests.
 */
@RunWith(JUnitParamsRunner.class)
public class CloudBackendSupportTest {

  private static final RetryTuning INSTANT =
      new RetryTuning(Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0, 0);

  private static CloudBackendSupport support(int pace) {
    VoicedDialogueConfig config =
        new VoicedDialogueConfig() {
          @Override
          public int speakingPace() {
            return pace;
          }
        };
    return new CloudBackendSupport(config, VoicedDialogueConfig.TtsProvider.OPENROUTER, 2, INSTANT);
  }

  @Test
  public void warnOnceSurfacesEachFailureNoticeExactlyOnce() {
    CloudBackendSupport support = support(100);
    List<String> notices = new ArrayList<>();
    support.setNotice(notices::add);

    support.warnOnce("first failure");
    support.warnOnce("first failure");
    support.warnOnce("second failure");

    assertEquals(
        "a repeated failure says its piece once, a different one is not silenced by it",
        Arrays.asList("first failure", "second failure"),
        notices);
  }

  @Test
  public void missingKeyNoticeIsNotOnceGuarded() {
    CloudBackendSupport support = support(100);
    List<String> notices = new ArrayList<>();
    support.setNotice(notices::add);

    support.noticeMissingKey("no key");
    support.noticeMissingKey("no key");
    support.warnOnce("real failure");

    assertEquals(
        "the missing-key notice repeats and leaves the once-guard for a real failure",
        Arrays.asList("no key", "no key", "real failure"),
        notices);
  }

  @Test
  public void nullNoticeHookIsSafe() {
    CloudBackendSupport support = support(100);
    support.setNotice(null);
    support.warnOnce("dropped");
    support.noticeMissingKey("dropped");
  }

  @Test
  @Parameters(method = "speedPercentCases")
  public void speedPercentClampsToTheSupportedRange(int configured, int expected) {
    assertEquals(expected, support(configured).speedPercent());
  }

  private Object[] speedPercentCases() {
    return new Object[] {
      new Object[] {100, 100},
      new Object[] {50, 50},
      new Object[] {200, 200},
      new Object[] {10, 50},
      new Object[] {999, 200},
    };
  }

  @Test
  public void backoffWithZeroBudgetsReturnsImmediately() {
    long start = System.nanoTime();
    support(100).backoffBeforeNetworkRetry(1);
    assertTrue(
        "a zero base and jitter must not park the worker", CloudHttp.elapsedMs(start) < 1_000);
  }
}
