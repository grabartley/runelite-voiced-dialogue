package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class AiStudioRetryInfo {

  private static final String RETRY_INFO_TYPE = "type.googleapis.com/google.rpc.RetryInfo";

  private static final Pattern DURATION = Pattern.compile("^(\\d+)(?:\\.(\\d{1,9}))?s$");

  private static final long MILLIS_PER_SECOND = 1_000;

  private AiStudioRetryInfo() {}

  static long retryDelayMillis(Gson gson, byte[] body) {
    for (JsonObject detail : AiStudioErrorDetails.ofType(gson, body, RETRY_INFO_TYPE)) {
      long millis = durationMillis(AiStudioErrorDetails.text(detail, "retryDelay"));
      if (millis > 0) {
        return millis;
      }
    }
    return 0;
  }

  static long durationMillis(String duration) {
    if (duration == null) {
      return 0;
    }
    Matcher matched = DURATION.matcher(duration.trim());
    if (!matched.matches()) {
      return 0;
    }
    try {
      long seconds = Long.parseLong(matched.group(1));
      String fraction = matched.group(2);
      long millis = Math.multiplyExact(seconds, MILLIS_PER_SECOND);
      if (fraction != null) {
        millis += Long.parseLong((fraction + "00").substring(0, 3));
      }
      return millis;
    } catch (RuntimeException e) {
      log.debug("[TTS cloud] AI Studio retry delay '{}' is not a usable duration", duration);
      return 0;
    }
  }
}
