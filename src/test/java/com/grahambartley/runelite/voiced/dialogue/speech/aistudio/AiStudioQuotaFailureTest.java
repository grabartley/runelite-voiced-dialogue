package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import org.junit.Test;

/**
 * Reading the {@code google.rpc.QuotaFailure} violation out of a Gemini API 429 and wording the
 * player's notice from it. The bodies are the shapes the API really returns.
 */
public class AiStudioQuotaFailureTest {

  /** A billed key that ran out of the model's per-day request allowance. */
  private static final String PAID_DAILY_CAP =
      "{\"error\": {\"code\": 429, \"message\": \"You exceeded your current quota.\","
          + " \"status\": \"RESOURCE_EXHAUSTED\", \"details\": ["
          + "{\"@type\": \"type.googleapis.com/google.rpc.QuotaFailure\", \"violations\": ["
          + "{\"quotaMetric\":"
          + " \"generativelanguage.googleapis.com/generate_requests_per_model_per_day\","
          + " \"quotaId\": \"GenerateRequestsPerDayPerProjectPerModel\","
          + " \"quotaDimensions\": {\"model\": \"gemini-3.1-flash-tts\", \"location\":"
          + " \"global\"}, \"quotaValue\": \"100\"}]},"
          + "{\"@type\": \"type.googleapis.com/google.rpc.RetryInfo\","
          + " \"retryDelay\": \"2917s\"}]}}";

  /** The same rejection on a key that has never had billing enabled. */
  private static final String FREE_TIER_CAP =
      "{\"error\": {\"code\": 429, \"status\": \"RESOURCE_EXHAUSTED\", \"details\": ["
          + "{\"@type\": \"type.googleapis.com/google.rpc.QuotaFailure\", \"violations\": ["
          + "{\"quotaMetric\":"
          + " \"generativelanguage.googleapis.com/generate_content_free_tier_requests\","
          + " \"quotaId\": \"GenerateRequestsPerDayPerProjectPerModel-FreeTier\","
          + " \"quotaDimensions\": {\"model\": \"gemini-3.1-flash-tts\", \"location\":"
          + " \"global\"}, \"quotaValue\": \"15\"}]}]}}";

  /** A 429 that carries no quota details at all, e.g. an edge-proxied rejection. */
  private static final String NO_DETAILS =
      "{\"error\": {\"code\": 429, \"message\": \"Resource has been exhausted.\","
          + " \"status\": \"RESOURCE_EXHAUSTED\"}}";

  private final Gson gson = new Gson();

  @Test
  public void aPaidPerModelCapIsReadOutOfTheViolation() {
    AiStudioQuotaFailure quota = parse(PAID_DAILY_CAP);

    assertEquals("GenerateRequestsPerDayPerProjectPerModel", quota.quotaId);
    assertEquals("100", quota.quotaValue);
    assertEquals("gemini-3.1-flash-tts", quota.model);
    assertEquals("daily", quota.period());
    assertFalse("a paid per-model cap is not a free-tier one", quota.isFreeTier());
  }

  @Test
  public void aFreeTierMetricIsRecognisedFromEitherSpelling() {
    assertTrue(parse(FREE_TIER_CAP).isFreeTier());
    assertTrue(
        "the id alone carries the marker when the metric does not",
        parse(violation("GenerateRequestsPerDayPerProjectPerModel-FreeTier", "", "5", ""))
            .isFreeTier());
    assertTrue(
        "and the metric alone when the id does not",
        parse(violation("", "generate_content_free_tier_requests", "5", "")).isFreeTier());
  }

  @Test
  public void aPerMinuteQuotaNamesItsOwnPeriod() {
    assertEquals(
        "per-minute",
        parse(violation("GenerateRequestsPerMinutePerProjectPerModel", "", "10", "")).period());
    assertEquals(
        "a quota naming no period claims none",
        "",
        parse(violation("GenerateContentPerProject", "", "10", "")).period());
  }

  @Test
  public void aBodyWithoutAQuotaFailureParsesToNothing() {
    assertNull(parse(NO_DETAILS));
    assertNull("a truncated body must not throw", parse("{\"error\": {\"details\": [{\"@ty"));
    assertNull(parse("not json at all"));
    assertNull(parse(""));
    assertNull(AiStudioQuotaFailure.parse(gson, null));
  }

  @Test
  public void aPaidCapNamesTheCapAndNeverAsksForBilling() {
    String notice = AiStudioQuotaFailure.noticeFor(gson, PAID_DAILY_CAP.getBytes(UTF_8));

    assertTrue("the model is named: " + notice, notice.contains("gemini-3.1-flash-tts"));
    assertTrue("as is the cap it hit: " + notice, notice.contains("daily request cap of 100"));
    assertTrue(
        "billing is already on, so the notice says so rather than asking for it",
        notice.contains("Enabling billing does not lift this cap"));
    assertFalse("the free tier is not the cause here", notice.contains("free tier"));
    assertTrue("the working alternative is still offered", notice.contains("OpenRouter"));
  }

  @Test
  public void aFreeTierCapKeepsTheEnableBillingAdvice() {
    assertEquals(
        AiStudioQuotaFailure.FREE_TIER_QUOTA_NOTICE,
        AiStudioQuotaFailure.noticeFor(gson, FREE_TIER_CAP.getBytes(UTF_8)));
  }

  @Test
  public void anUnreadableBodyFallsBackToTheGenericNotice() {
    assertEquals(
        AiStudioQuotaFailure.QUOTA_NOTICE,
        AiStudioQuotaFailure.noticeFor(gson, NO_DETAILS.getBytes(UTF_8)));
    assertEquals(
        AiStudioQuotaFailure.QUOTA_NOTICE, AiStudioQuotaFailure.noticeFor(gson, new byte[0]));
    assertFalse(
        "an unknown cause must not be blamed on the free tier",
        AiStudioQuotaFailure.QUOTA_NOTICE.contains("free tier"));
  }

  @Test
  public void aViolationMissingItsDimensionsStillWordsANotice() {
    String notice =
        AiStudioQuotaFailure.noticeFor(
            gson, violation("GenerateRequestsPerProject", "", "", "").getBytes(UTF_8));

    assertEquals(
        "Google AI Studio stopped voicing dialogue: the speech model has reached its request cap."
            + " Enabling billing does not lift this cap, so wait for it to reset, or switch Voice"
            + " Provider to OpenRouter.",
        notice);
  }

  private AiStudioQuotaFailure parse(String body) {
    return AiStudioQuotaFailure.parse(gson, body.getBytes(UTF_8));
  }

  /** A 429 body carrying one violation with the given fields, any of which may be blank. */
  private static String violation(
      String quotaId, String quotaMetric, String quotaValue, String model) {
    StringBuilder body =
        new StringBuilder(
            "{\"error\": {\"details\": [{\"@type\":"
                + " \"type.googleapis.com/google.rpc.QuotaFailure\", \"violations\": [{");
    body.append("\"quotaId\": \"").append(quotaId).append('"');
    body.append(", \"quotaMetric\": \"").append(quotaMetric).append('"');
    if (!quotaValue.isEmpty()) {
      body.append(", \"quotaValue\": \"").append(quotaValue).append('"');
    }
    if (!model.isEmpty()) {
      body.append(", \"quotaDimensions\": {\"model\": \"").append(model).append("\"}");
    }
    return body.append("}]}]}}").toString();
  }
}
