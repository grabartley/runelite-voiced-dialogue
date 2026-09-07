package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * The {@code google.rpc.RetryInfo} hint a Gemini API 429 carries: how long the server says to wait
 * before asking again, as a protobuf duration such as {@code 2917s}.
 *
 * <p>This is the provider stating a fact rather than the client guessing one. A locally computed
 * back-off that expires first buys nothing: every call made before the stated moment earns another
 * 429, so the wait is the only thing that ends the refusal.
 */
@Slf4j
final class AiStudioRetryInfo {

  private static final String RETRY_INFO_TYPE = "type.googleapis.com/google.rpc.RetryInfo";

  /** A protobuf duration: seconds, optionally fractional, always suffixed {@code s}. */
  private static final Pattern DURATION = Pattern.compile("^(\\d+)(?:\\.(\\d{1,9}))?s$");

  private static final long MILLIS_PER_SECOND = 1_000;

  private AiStudioRetryInfo() {}

  /**
   * How long the rejection asks the caller to wait, in milliseconds, or {@code 0} when it carries
   * no usable hint. An unreadable or nonsense duration reads as no hint, leaving the caller's own
   * back-off to decide.
   */
  static long retryDelayMillis(Gson gson, byte[] body) {
    for (JsonObject detail : AiStudioErrorDetails.ofType(gson, body, RETRY_INFO_TYPE)) {
      long millis = durationMillis(AiStudioErrorDetails.text(detail, "retryDelay"));
      if (millis > 0) {
        return millis;
      }
    }
    return 0;
  }

  /** {@code "2917s"} as 2917000, {@code "0.5s"} as 500, anything unrecognised as 0. */
  static long durationMillis(String duration) {
    Matcher matched = DURATION.matcher(duration.trim());
    if (!matched.matches()) {
      return 0;
    }
    try {
      long seconds = Long.parseLong(matched.group(1));
      String fraction = matched.group(2);
      long millis = Math.multiplyExact(seconds, MILLIS_PER_SECOND);
      if (fraction != null) {
        // Pad to milliseconds and drop the finer digits a wait cannot use.
        millis += Long.parseLong((fraction + "00").substring(0, 3));
      }
      return millis;
    } catch (RuntimeException e) {
      // A duration too large for a long is as unusable as a malformed one.
      log.debug("[TTS cloud] AI Studio retry delay '{}' is not a usable duration", duration);
      return 0;
    }
  }
}
