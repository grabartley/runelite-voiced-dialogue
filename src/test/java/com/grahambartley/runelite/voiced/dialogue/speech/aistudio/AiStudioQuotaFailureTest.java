package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.grahambartley.runelite.voiced.dialogue.speech.aistudio.AiStudioQuotaFailure.Period;
import org.junit.Test;

public class AiStudioQuotaFailureTest {

  private static final String PAID_DAILY_CAP = AiStudioResponses.dailyCapExhausted();

  private static final String FREE_TIER_CAP =
      AiStudioResponses.quotaFailure(
          "GenerateRequestsPerDayPerProjectPerModel-FreeTier",
          "generativelanguage.googleapis.com/generate_content_free_tier_requests",
          "15",
          "gemini-3.1-flash-tts");

  private final Gson gson = new Gson();

  @Test
  public void aPaidPerModelCapIsReadOutOfTheViolation() {
    AiStudioQuotaFailure quota = parse(PAID_DAILY_CAP);

    assertEquals("GenerateRequestsPerDayPerProjectPerModel", quota.quotaId);
    assertEquals("100", quota.quotaValue);
    assertEquals("gemini-3.1-flash-tts", quota.model);
    assertEquals(Period.DAILY, quota.period());
    assertFalse("a paid per-model cap is not a free-tier one", quota.isFreeTier());
  }

  @Test
  public void aFreeTierMetricIsRecognisedFromEitherSpelling() {
    assertTrue(parse(FREE_TIER_CAP).isFreeTier());
    assertTrue(
        "the id alone carries the marker when the metric does not",
        parse(quota("GenerateRequestsPerDayPerProjectPerModel-FreeTier", "")).isFreeTier());
    assertTrue(
        "and the metric alone when the id does not",
        parse(quota("", "generate_content_free_tier_requests")).isFreeTier());
    assertTrue(
        "however the marker is separated",
        parse(quota("GenerateRequests-Free-Tier", "")).isFreeTier());
  }

  @Test
  public void aQuotaPeriodIsReadFromWhicheverFieldNamesIt() {
    assertEquals(
        Period.PER_MINUTE,
        parse(quota("GenerateRequestsPerMinutePerProjectPerModel", "")).period());
    assertEquals(Period.DAILY, parse(quota("", "generate_requests_per_model_per_day")).period());
    assertEquals(
        "a quota naming no period claims none",
        Period.UNKNOWN,
        parse(quota("GenerateContentPerProject", "")).period());
  }

  @Test
  public void aBodyWithoutAQuotaFailureParsesToNothing() {
    assertNull(parse(AiStudioResponses.quotaExhausted()));
    assertNull("a truncated body must not throw", parse("{\"error\": {\"details\": [{\"@ty"));
    assertNull(parse("not json at all"));
    assertNull(parse(""));
    assertNull(AiStudioQuotaFailure.parse(gson, null));
  }

  @Test
  public void aMalformedDetailDoesNotHideAViolationBehindIt() {
    String body =
        "{\"error\": {\"details\": [\"not an object\", {\"@type\":"
            + " \"type.googleapis.com/google.rpc.QuotaFailure\", \"violations\":"
            + " [{\"quotaDimensions\": {\"model\": \"gemini-3.1-flash-tts\"}}]}]}}";

    assertEquals("gemini-3.1-flash-tts", parse(body).model);
  }

  @Test
  public void violationsOfTheWrongShapeFallBackRatherThanThrow() {
    assertNull("an object where an array belongs is not a violation", parse(violations("{}")));
    assertNull(parse(violations("7")));
    assertEquals(
        "and the player still gets a quota notice rather than an unexplained failure",
        AiStudioQuotaFailure.QUOTA_NOTICE,
        noticeFor(violations("7")));
  }

  @Test
  public void aPaidCapNamesTheCapAndNeverAsksForBilling() {
    String notice = noticeFor(PAID_DAILY_CAP);

    assertTrue("the model is named: " + notice, notice.contains("gemini-3.1-flash-tts"));
    assertTrue("as is the cap it hit: " + notice, notice.contains("daily request cap of 100"));
    assertTrue(
        "billing is already on, so the notice says so rather than asking for it",
        notice.contains("Enabling billing does not lift this cap"));
    assertFalse("the free tier is not the cause here", notice.contains("free tier"));
    assertTrue("the working alternative is still offered", notice.contains("OpenRouter"));
  }

  @Test
  public void aFreeTierCapKeepsTheEnableBillingAdviceAndNamesTheCeiling() {
    String notice = noticeFor(FREE_TIER_CAP);

    assertTrue("the ceiling is named: " + notice, notice.contains("daily request cap of 15"));
    assertTrue(
        "the free tier is the one case billing lifts",
        notice.contains("Enable billing at aistudio.google.com to lift it"));
  }

  @Test
  public void markupInAReportedValueCannotBreakTheChatLine() {
    String notice =
        noticeFor(AiStudioResponses.quotaFailure("GenerateRequestsPerDay", "", "1<0>0", "<b>"));

    assertFalse("no markup survives from the response: " + notice, notice.contains("<"));
    assertFalse(notice.contains(">"));
  }

  @Test
  public void aMalformedViolationDoesNotHideAWellFormedOneBehindIt() {
    String body = violations("[7, {\"quotaDimensions\": {\"model\": \"gemini-3.1-flash-tts\"}}]");

    assertEquals("gemini-3.1-flash-tts", parse(body).model);
  }

  @Test
  public void aPerMinuteLimitReadsAsAPauseRatherThanAProviderSwitch() {
    String notice = noticeFor(quota("GenerateRequestsPerMinutePerProjectPerModel", ""));

    assertTrue("the ceiling is named: " + notice, notice.contains("per-minute request cap of 10"));
    assertTrue("a limit that clears itself says so", notice.contains("Lines resume"));
    assertFalse(
        "a minute of waiting is not worth changing provider over", notice.contains("OpenRouter"));
  }

  @Test
  public void aFreeTierPerMinuteLimitIsStillAPauseRatherThanAnAccountProblem() {
    String notice =
        noticeFor(
            quota(
                "GenerateRequestsPerMinutePerProjectPerModel-FreeTier",
                "generativelanguage.googleapis.com/generate_content_free_tier_requests"));

    assertTrue("a limit that clears itself says so", notice.contains("Lines resume"));
    assertFalse(
        "60 seconds of waiting is not an account to go and fix", notice.contains("enable billing"));
  }

  @Test
  public void anUnreadableBodyFallsBackToTheGenericNotice() {
    assertEquals(AiStudioQuotaFailure.QUOTA_NOTICE, noticeFor(AiStudioResponses.quotaExhausted()));
    assertEquals(
        AiStudioQuotaFailure.QUOTA_NOTICE, AiStudioQuotaFailure.noticeFor(gson, new byte[0]));
    assertFalse(
        "an unknown cause must not be blamed on the free tier",
        AiStudioQuotaFailure.QUOTA_NOTICE.contains("free tier"));
  }

  @Test
  public void aViolationMissingItsDimensionsStillWordsANotice() {
    assertEquals(
        "Google AI Studio stopped voicing dialogue: the speech model has reached its request cap."
            + " Check your quota at aistudio.google.com, or switch Voice Provider to OpenRouter.",
        noticeFor(AiStudioResponses.quotaFailure("GenerateRequestsPerProject", "", "", "")));
  }

  @Test
  public void aQuotaNamingNeitherPeriodNorTierClaimsNeither() {
    String notice = noticeFor(quota("GenerateRequestsPerProject", ""));

    assertFalse("nothing says how long it holds", notice.contains("wait for it to reset"));
    assertFalse("nor whether billing would lift it", notice.contains("billing"));
  }

  @Test
  public void aTokenQuotaIsNotReportedAsARequestCap() {
    String notice =
        noticeFor(
            quota(
                "GenerateContentInputTokensPerModelPerMinute",
                "generativelanguage.googleapis.com/generate_content_input_token_count"));

    assertTrue("tokens are not requests: " + notice, notice.contains("per-minute token cap of 10"));
  }

  @Test
  public void theDebugLineCarriesEveryFieldTheVerdictRestsOn() {
    String logged = parse(PAID_DAILY_CAP).toString();

    assertTrue(logged.contains("quotaId=GenerateRequestsPerDayPerProjectPerModel"));
    assertTrue("the metric decides the free-tier verdict", logged.contains("generate_requests"));
    assertTrue(logged.contains("quotaValue=100"));
    assertTrue(logged.contains("model=gemini-3.1-flash-tts"));
  }

  private static String violations(String value) {
    return "{\"error\": {\"code\": 429, \"details\": [{\"@type\":"
        + " \"type.googleapis.com/google.rpc.QuotaFailure\", \"violations\": "
        + value
        + "}]}}";
  }

  private static String quota(String quotaId, String quotaMetric) {
    return AiStudioResponses.quotaFailure(quotaId, quotaMetric, "10", "");
  }

  private AiStudioQuotaFailure parse(String body) {
    return AiStudioQuotaFailure.parse(gson, body.getBytes(UTF_8));
  }

  private String noticeFor(String body) {
    return AiStudioQuotaFailure.noticeFor(gson, body.getBytes(UTF_8));
  }
}
