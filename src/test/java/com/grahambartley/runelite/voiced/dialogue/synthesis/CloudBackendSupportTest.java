package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The shared backend plumbing: notice guarding, the speaking-pace clamp, and the small response/PCM
 * helpers. The retry loops and failure logging that use it are pinned by each backend's own tests.
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
    return new CloudBackendSupport(config, 2, INSTANT);
  }

  @Test
  public void warnOnceSurfacesOnlyTheFirstFailureNotice() {
    CloudBackendSupport support = support(100);
    List<String> notices = new ArrayList<>();
    support.setNotice(notices::add);

    support.warnOnce("first failure");
    support.warnOnce("second failure");

    assertEquals("only the first failure reaches the user", 1, notices.size());
    assertEquals("first failure", notices.get(0));
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
        "a zero base and jitter must not park the worker",
        CloudBackendSupport.elapsedMs(start) < 1_000);
  }

  @Test
  public void flattenConcatenatesChunksInOrder() {
    List<float[]> chunks =
        Arrays.asList(new float[] {1f, 2f}, new float[] {}, new float[] {3f, 4f, 5f});
    assertArrayEquals(new float[] {1f, 2f, 3f, 4f, 5f}, CloudBackendSupport.flatten(chunks, 5), 0f);
  }

  @Test
  public void isNonBlankRequiresANonWhitespaceCharacter() {
    assertFalse(CloudBackendSupport.isNonBlank(null));
    assertFalse(CloudBackendSupport.isNonBlank(""));
    assertFalse(CloudBackendSupport.isNonBlank("   "));
    assertTrue(CloudBackendSupport.isNonBlank("key"));
    assertTrue(CloudBackendSupport.isNonBlank("  key  "));
  }

  @Test
  public void headerOrEmptyNeverReturnsNull() {
    Response response = response().newBuilder().header("Content-Type", "audio/pcm").build();
    assertEquals("audio/pcm", CloudBackendSupport.headerOrEmpty(response, "Content-Type"));
    assertEquals("", CloudBackendSupport.headerOrEmpty(response, "X-Missing"));
  }

  @Test
  public void errorBodyReadsTheBytesAndTreatsAMissingBodyAsEmpty() {
    Response withBody =
        response()
            .newBuilder()
            .body(ResponseBody.create(MediaType.parse("text/plain"), "quota exceeded"))
            .build();
    assertArrayEquals("quota exceeded".getBytes(), CloudBackendSupport.errorBody(withBody));
    assertArrayEquals(new byte[0], CloudBackendSupport.errorBody(response()));
  }

  private static Response response() {
    return new Response.Builder()
        .request(new Request.Builder().url("http://localhost/").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .build();
  }
}
