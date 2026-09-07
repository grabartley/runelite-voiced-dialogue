package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import org.junit.Test;

/** Reading the {@code google.rpc.RetryInfo} wait a Gemini API 429 states. */
public class AiStudioRetryInfoTest {

  private final Gson gson = new Gson();

  @Test
  public void theStatedDelayIsReadOutOfTheRejection() {
    assertEquals(
        2_917_000,
        AiStudioRetryInfo.retryDelayMillis(
            gson, AiStudioResponses.retryAfter("2917s").getBytes(UTF_8)));
  }

  @Test
  public void aRejectionStatingNoDelayReadsAsNoHint() {
    assertEquals(
        0,
        AiStudioRetryInfo.retryDelayMillis(
            gson, AiStudioResponses.quotaExhausted().getBytes(UTF_8)));
    assertEquals(0, AiStudioRetryInfo.retryDelayMillis(gson, "not json".getBytes(UTF_8)));
    assertEquals(0, AiStudioRetryInfo.retryDelayMillis(gson, new byte[0]));
    assertEquals(0, AiStudioRetryInfo.retryDelayMillis(gson, null));
  }

  @Test
  public void aQuotaFailureAheadOfTheHintDoesNotHideIt() {
    // The shape Google really sends: the quota violation first, the retry hint behind it.
    byte[] body =
        AiStudioResponses.quotaFailure(
                "GenerateRequestsPerDayPerProjectPerModel", "", "100", "gemini-3.1-flash-tts")
            .getBytes(UTF_8);

    assertEquals(2_917_000, AiStudioRetryInfo.retryDelayMillis(gson, body));
  }

  @Test
  public void aFractionalDelayKeepsItsMilliseconds() {
    assertEquals(500, AiStudioRetryInfo.durationMillis("0.5s"));
    assertEquals(1_250, AiStudioRetryInfo.durationMillis("1.25s"));
    assertEquals(1_123, AiStudioRetryInfo.durationMillis("1.123456789s"));
    assertEquals(
        "whitespace around the value is not a malformation",
        7_000,
        AiStudioRetryInfo.durationMillis(" 7s "));
  }

  @Test
  public void aMalformedDelayReadsAsNoHint() {
    assertEquals("a duration must carry its unit", 0, AiStudioRetryInfo.durationMillis("2917"));
    assertEquals(0, AiStudioRetryInfo.durationMillis("soon"));
    assertEquals("a negative wait is not a wait", 0, AiStudioRetryInfo.durationMillis("-5s"));
    assertEquals(0, AiStudioRetryInfo.durationMillis("5m"));
    assertEquals(0, AiStudioRetryInfo.durationMillis(""));
  }

  @Test
  public void aDelayTooLargeToMeasureReadsAsNoHint() {
    assertEquals(
        "an unrepresentable wait is as unusable as a malformed one",
        0,
        AiStudioRetryInfo.durationMillis("99999999999999999999s"));
    assertEquals(0, AiStudioRetryInfo.durationMillis(Long.MAX_VALUE + "s"));
  }
}
