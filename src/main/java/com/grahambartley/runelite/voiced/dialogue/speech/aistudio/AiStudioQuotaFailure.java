package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

/**
 * The {@code google.rpc.QuotaFailure} violation a Gemini API 429 carries, and the one-time notice a
 * player is shown for it: which quota ran out, the limit it enforces, and the model it applies to.
 *
 * <p>A free-tier ceiling and a paid per-model cap arrive in the same response shape and are told
 * apart only by the metric name, so reading the violation is the only way the notice can state what
 * actually happened. Assuming the free tier tells a billed key to enable billing it already has,
 * for a cap that billing does not lift.
 */
@Slf4j
final class AiStudioQuotaFailure {

  /**
   * The notice for a 429 whose body does not say which quota ran out. It names no cause on purpose:
   * the same status covers a free-tier ceiling, a paid per-model cap, and a per-minute rate limit,
   * and naming the wrong one sends the player after an account problem they do not have.
   */
  static final String QUOTA_NOTICE =
      "Your Google AI Studio quota was hit, so dialogue cannot be voiced right now. Check your"
          + " quota at aistudio.google.com, or switch Voice Provider to OpenRouter.";

  /** The notice for a reported free-tier quota, the one case enabling billing does lift. */
  static final String FREE_TIER_QUOTA_NOTICE =
      "Your Google AI Studio free-tier quota was hit, so dialogue cannot be voiced right now. The"
          + " free tier allows only a handful of speech requests per day, so enable billing at"
          + " aistudio.google.com, or switch Voice Provider to OpenRouter.";

  private static final String QUOTA_FAILURE_TYPE = "type.googleapis.com/google.rpc.QuotaFailure";

  /** Free-tier metrics and quota ids both carry this once case and separators are normalised. */
  private static final String FREE_TIER_MARKER = "freetier";

  private static final String PER_DAY_MARKER = "perday";

  private static final String PER_MINUTE_MARKER = "perminute";

  /** The quota's identifier, e.g. {@code GenerateRequestsPerDayPerProjectPerModel}. */
  final String quotaId;

  /** The metered metric, e.g. {@code .../generate_requests_per_model_per_day}. */
  final String quotaMetric;

  /** The limit the quota enforces, as reported; empty when the response omits it. */
  final String quotaValue;

  /** The {@code model} dimension the quota applies to; empty when the response omits it. */
  final String model;

  private AiStudioQuotaFailure(
      String quotaId, String quotaMetric, String quotaValue, String model) {
    this.quotaId = quotaId;
    this.quotaMetric = quotaMetric;
    this.quotaValue = quotaValue;
    this.model = model;
  }

  /** The one-time user notice for a 429, worded from whatever quota the body reports. */
  static String noticeFor(Gson gson, byte[] body) {
    AiStudioQuotaFailure quota = parse(gson, body);
    if (quota == null) {
      return QUOTA_NOTICE;
    }
    log.debug("[TTS cloud] AI Studio quota exhausted: {}", quota);
    return quota.isFreeTier() ? FREE_TIER_QUOTA_NOTICE : quota.capNotice();
  }

  /**
   * The first quota violation reported in an error body, or {@code null} when the body carries none
   * or cannot be read, so a response shape change degrades to the generic notice.
   */
  static AiStudioQuotaFailure parse(Gson gson, byte[] body) {
    if (body == null || body.length == 0) {
      return null;
    }
    try {
      JsonObject document =
          gson.fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
      JsonObject error = document == null ? null : document.getAsJsonObject("error");
      JsonArray details = error == null ? null : error.getAsJsonArray("details");
      if (details == null) {
        return null;
      }
      for (JsonElement element : details) {
        JsonObject detail = element.getAsJsonObject();
        if (!QUOTA_FAILURE_TYPE.equals(asText(detail, "@type"))) {
          continue;
        }
        JsonArray violations = detail.getAsJsonArray("violations");
        if (violations == null || violations.size() == 0) {
          continue;
        }
        return of(violations.get(0).getAsJsonObject());
      }
      return null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** Whether the exhausted quota is a free-tier one, which enabling billing does lift. */
  boolean isFreeTier() {
    return normalised().contains(FREE_TIER_MARKER);
  }

  /**
   * How often the quota resets, worded for the notice: {@code "daily"}, {@code "per-minute"}, or
   * empty when the reported quota names no period.
   */
  String period() {
    String normalised = normalised();
    if (normalised.contains(PER_DAY_MARKER)) {
      return "daily";
    }
    if (normalised.contains(PER_MINUTE_MARKER)) {
      return "per-minute";
    }
    return "";
  }

  @Override
  public String toString() {
    return "quotaId=" + quotaId + " quotaValue=" + quotaValue + " model=" + model;
  }

  /**
   * The notice for a quota billing has already paid for. It must not repeat the enable-billing
   * advice: a paid per-model cap is enforced on a billed key and stays where it is when billing is
   * turned on again.
   */
  private String capNotice() {
    StringBuilder notice = new StringBuilder("Google AI Studio stopped voicing dialogue: ");
    notice.append(model.isEmpty() ? "the speech model" : model);
    notice.append(" has reached its ");
    if (!period().isEmpty()) {
      notice.append(period()).append(' ');
    }
    notice.append("request cap");
    if (!quotaValue.isEmpty()) {
      notice.append(" of ").append(quotaValue);
    }
    return notice
        .append(". Enabling billing does not lift this cap, so wait for it to reset, or switch")
        .append(" Voice Provider to OpenRouter.")
        .toString();
  }

  private static AiStudioQuotaFailure of(JsonObject violation) {
    JsonObject dimensions = violation.getAsJsonObject("quotaDimensions");
    return new AiStudioQuotaFailure(
        asText(violation, "quotaId"),
        asText(violation, "quotaMetric"),
        asText(violation, "quotaValue"),
        asText(dimensions, "model"));
  }

  /** Id and metric as one lower-case separator-free string, so either spelling matches a marker. */
  private String normalised() {
    return (quotaId + quotaMetric).toLowerCase(Locale.ROOT).replace("_", "");
  }

  private static String asText(JsonObject object, String field) {
    if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
      return "";
    }
    try {
      return object.get(field).getAsString();
    } catch (RuntimeException e) {
      return "";
    }
  }
}
