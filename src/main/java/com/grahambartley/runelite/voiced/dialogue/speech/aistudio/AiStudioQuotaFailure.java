package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * The {@code google.rpc.QuotaFailure} violation a Gemini API 429 carries, and the one-time notice a
 * player is shown for it: which quota ran out, the limit it enforces, and the model it applies to.
 *
 * <p>A free-tier ceiling, a paid per-model cap, and a per-minute rate limit arrive in the same
 * response shape and are told apart only by the metric name, so reading the violation is the only
 * way the notice can state what actually happened. A notice that assumes the free tier tells a
 * billed key to enable billing it already has, for a cap that billing does not lift.
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

  private static final String QUOTA_FAILURE_TYPE = "type.googleapis.com/google.rpc.QuotaFailure";

  /** Strips the separators an id and a metric spell differently, so one marker matches both. */
  private static final Pattern SEPARATORS = Pattern.compile("[^A-Za-z0-9]+");

  /** Google words this marker as {@code free_tier}, {@code FreeTier}, and {@code -FreeTier}. */
  private static final String FREE_TIER_MARKER = "freetier";

  private static final String PER_DAY_MARKER = "perday";

  private static final String PER_MINUTE_MARKER = "perminute";

  /** Google meters tokens through the same violation shape it meters requests through. */
  private static final String TOKEN_MARKER = "token";

  private static final Pattern MARKUP = Pattern.compile("[<>]");

  /** The quota's identifier, e.g. {@code GenerateRequestsPerDayPerProjectPerModel}. */
  final String quotaId;

  /** The metered metric, e.g. {@code .../generate_requests_per_model_per_day}. */
  final String quotaMetric;

  /** The limit the quota enforces, as reported; empty when the response omits it. */
  final String quotaValue;

  /** The {@code model} dimension the quota applies to; empty when the response omits it. */
  final String model;

  /** Id and metric as one lower-case separator-free string, so either spelling matches a marker. */
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

  /** The one-time user notice for a 429, worded from whatever quota the body reports. */
  static String noticeFor(Gson gson, byte[] body) {
    AiStudioQuotaFailure quota = parse(gson, body);
    if (quota == null) {
      return QUOTA_NOTICE;
    }
    log.debug("[TTS cloud] AI Studio quota exhausted: {}", quota);
    return quota.notice();
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
        if (!element.isJsonObject()) {
          continue;
        }
        JsonObject detail = element.getAsJsonObject();
        if (!QUOTA_FAILURE_TYPE.equals(asText(detail, "@type"))) {
          continue;
        }
        JsonArray violations = detail.getAsJsonArray("violations");
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
    } catch (RuntimeException e) {
      log.debug("[TTS cloud] AI Studio quota failure parse error: {}", e.getMessage());
      return null;
    }
  }

  /** Whether the exhausted quota is a free-tier one, which enabling billing does lift. */
  boolean isFreeTier() {
    return normalised.contains(FREE_TIER_MARKER);
  }

  /** Whether the quota meters tokens rather than requests, which the notice must not confuse. */
  boolean metersTokens() {
    return normalised.contains(TOKEN_MARKER);
  }

  /** How often the reported quota resets, which decides how urgently the notice reads. */
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

  /** How often a quota resets, and the word the notice uses for it. */
  enum Period {
    DAILY("daily"),
    PER_MINUTE("per-minute"),

    /** A quota naming no period, which the notice then claims none for. */
    UNKNOWN("");

    final String label;

    Period(String label) {
      this.label = label;
    }
  }

  /**
   * The notice this violation reads as. The period is decided before the tier, because a per-minute
   * ceiling clears within the minute on any tier and the rate-limit back-off already spaces the
   * next line: telling a free-tier player to enable billing for that is the same misdirection as
   * telling a billed one to enable billing they already have.
   */
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
    // A quota naming neither a period nor a tier: nothing in the body backs a claim about when it
    // clears or whether billing would lift it, so the notice makes neither.
    return cap(period)
        .append(". Check your quota at aistudio.google.com, or switch Voice Provider to")
        .append(" OpenRouter.")
        .toString();
  }

  /** The opening clause naming what ran out, which every worded cap notice shares. */
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
    // The two fields the notice echoes reach a chat line wrapped in a colour tag, so a stray angle
    // bracket in a response would break the markup around the whole message.
    return new AiStudioQuotaFailure(
        asText(violation, "quotaId"),
        asText(violation, "quotaMetric"),
        MARKUP.matcher(asText(violation, "quotaValue")).replaceAll(""),
        MARKUP.matcher(asText(dimensions, "model")).replaceAll(""));
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
