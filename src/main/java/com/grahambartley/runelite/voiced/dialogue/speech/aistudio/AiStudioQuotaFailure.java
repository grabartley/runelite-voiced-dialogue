package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class AiStudioQuotaFailure {

  static final String QUOTA_NOTICE =
      "Your Google AI Studio quota was hit, so dialogue cannot be voiced right now. Check your"
          + " quota at aistudio.google.com, or switch Voice Provider to OpenRouter.";

  private static final String QUOTA_FAILURE_TYPE = "type.googleapis.com/google.rpc.QuotaFailure";

  private static final Pattern SEPARATORS = Pattern.compile("[^A-Za-z0-9]+");

  private static final String FREE_TIER_MARKER = "freetier";

  private static final String PER_DAY_MARKER = "perday";

  private static final String PER_MINUTE_MARKER = "perminute";

  private static final String TOKEN_MARKER = "token";

  private static final Pattern MARKUP = Pattern.compile("[<>]");

  final String quotaId;

  final String quotaMetric;

  final String quotaValue;

  final String model;

  private final String normalised;

  private AiStudioQuotaFailure(
      String quotaId, String quotaMetric, String quotaValue, String model) {
    this.quotaId = quotaId;
    this.quotaMetric = quotaMetric;
    this.quotaValue = quotaValue;
    this.model = model;
    this.normalised =
        SEPARATORS.matcher(quotaId + quotaMetric).replaceAll("").toLowerCase(Locale.ROOT);
  }

  static String noticeFor(Gson gson, byte[] body) {
    AiStudioQuotaFailure quota = parse(gson, body);
    if (quota == null) {
      return QUOTA_NOTICE;
    }
    log.debug("[TTS cloud] AI Studio quota exhausted: {}", quota);
    return quota.notice();
  }

  static AiStudioQuotaFailure parse(Gson gson, byte[] body) {
    for (JsonObject detail : AiStudioErrorDetails.ofType(gson, body, QUOTA_FAILURE_TYPE)) {
      JsonArray violations = AiStudioErrorDetails.array(detail, "violations");
      if (violations == null) {
        continue;
      }
      for (JsonElement violation : violations) {
        if (violation.isJsonObject()) {
          return of(violation.getAsJsonObject());
        }
      }
    }
    return null;
  }

  boolean isFreeTier() {
    return normalised.contains(FREE_TIER_MARKER);
  }

  boolean metersTokens() {
    return normalised.contains(TOKEN_MARKER);
  }

  Period period() {
    if (normalised.contains(PER_DAY_MARKER)) {
      return Period.DAILY;
    }
    if (normalised.contains(PER_MINUTE_MARKER)) {
      return Period.PER_MINUTE;
    }
    return Period.UNKNOWN;
  }

  @Override
  public String toString() {
    return "quotaId="
        + quotaId
        + " quotaMetric="
        + quotaMetric
        + " quotaValue="
        + quotaValue
        + " model="
        + model;
  }

  enum Period {
    DAILY("daily"),
    PER_MINUTE("per-minute"),

    UNKNOWN("");

    final String label;

    Period(String label) {
      this.label = label;
    }
  }

  private String notice() {
    Period period = period();
    if (period == Period.PER_MINUTE) {
      return cap(period).append(". Lines resume once the limit resets.").toString();
    }
    if (isFreeTier()) {
      return cap(period)
          .append(". Enable billing at aistudio.google.com to lift it, or switch Voice Provider")
          .append(" to OpenRouter.")
          .toString();
    }
    if (period == Period.DAILY) {
      return cap(period)
          .append(". Enabling billing does not lift this cap, so wait for it to reset, or switch")
          .append(" Voice Provider to OpenRouter.")
          .toString();
    }
    return cap(period)
        .append(". Check your quota at aistudio.google.com, or switch Voice Provider to")
        .append(" OpenRouter.")
        .toString();
  }

  private StringBuilder cap(Period period) {
    StringBuilder notice = new StringBuilder("Google AI Studio ");
    notice.append(period == Period.PER_MINUTE ? "paused dialogue: " : "stopped voicing dialogue: ");
    notice.append(model.isEmpty() ? "the speech model" : model);
    notice.append(" has reached its ");
    if (!period.label.isEmpty()) {
      notice.append(period.label).append(' ');
    }
    notice.append(metersTokens() ? "token cap" : "request cap");
    if (!quotaValue.isEmpty()) {
      notice.append(" of ").append(quotaValue);
    }
    return notice;
  }

  private static AiStudioQuotaFailure of(JsonObject violation) {
    JsonObject dimensions = violation.getAsJsonObject("quotaDimensions");
    return new AiStudioQuotaFailure(
        AiStudioErrorDetails.text(violation, "quotaId"),
        AiStudioErrorDetails.text(violation, "quotaMetric"),
        MARKUP.matcher(AiStudioErrorDetails.text(violation, "quotaValue")).replaceAll(""),
        MARKUP.matcher(AiStudioErrorDetails.text(dimensions, "model")).replaceAll(""));
  }
}
