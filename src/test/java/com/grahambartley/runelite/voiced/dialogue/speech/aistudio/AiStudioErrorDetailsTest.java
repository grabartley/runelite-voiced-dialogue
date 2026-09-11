package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.Test;

public class AiStudioErrorDetailsTest {

  private static final String QUOTA_FAILURE = "type.googleapis.com/google.rpc.QuotaFailure";

  private static final String RETRY_INFO = "type.googleapis.com/google.rpc.RetryInfo";

  private final Gson gson = new Gson();

  @Test
  public void eachReaderSeesOnlyTheTypeItAskedFor() {
    byte[] body =
        AiStudioResponses.quotaFailure("GenerateRequestsPerDay", "", "100", "gemini-3.1-flash-tts")
            .getBytes(UTF_8);

    assertEquals(1, ofType(body, QUOTA_FAILURE).size());
    assertEquals(
        AiStudioResponses.DAILY_CAP_RETRY_DELAY,
        AiStudioErrorDetails.text(ofType(body, RETRY_INFO).get(0), "retryDelay"));
  }

  @Test
  public void anUnreadableBodyYieldsNothingRatherThanThrowing() {
    assertTrue(ofType(AiStudioResponses.quotaExhausted().getBytes(UTF_8), RETRY_INFO).isEmpty());
    assertTrue(
        ofType("{\"error\": {\"details\": {\"not\": \"an array\"}}}".getBytes(UTF_8), RETRY_INFO)
            .isEmpty());
    assertTrue(ofType("{\"error\": [1, 2]}".getBytes(UTF_8), RETRY_INFO).isEmpty());
    assertTrue(ofType("not json".getBytes(UTF_8), RETRY_INFO).isEmpty());
    assertTrue(ofType(new byte[0], RETRY_INFO).isEmpty());
    assertTrue(AiStudioErrorDetails.ofType(gson, null, RETRY_INFO).isEmpty());
  }

  @Test
  public void aMalformedEntryDoesNotHideTheOnesAroundIt() {
    byte[] body =
        ("{\"error\": {\"details\": [7, {\"@type\": \""
                + RETRY_INFO
                + "\", \"retryDelay\": \"30s\"}]}}")
            .getBytes(UTF_8);

    assertEquals("30s", AiStudioErrorDetails.text(ofType(body, RETRY_INFO).get(0), "retryDelay"));
  }

  @Test
  public void aMissingOrUnreadableFieldReadsAsEmpty() {
    JsonObject detail = new JsonObject();
    detail.addProperty("retryDelay", "30s");
    detail.add("nulled", null);

    assertEquals("30s", AiStudioErrorDetails.text(detail, "retryDelay"));
    assertEquals("", AiStudioErrorDetails.text(detail, "absent"));
    assertEquals("", AiStudioErrorDetails.text(detail, "nulled"));
    assertEquals("", AiStudioErrorDetails.text(null, "retryDelay"));
  }

  private List<JsonObject> ofType(byte[] body, String type) {
    return AiStudioErrorDetails.ofType(gson, body, type);
  }
}
