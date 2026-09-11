package com.grahambartley.runelite.voiced.dialogue.speech;

final class CloudSynthTrace {

  private CloudSynthTrace() {}

  static String success(
      int attempt, int maxAttempts, long elapsedMs, int inputLen, int byteCount, String genId) {
    return "[TTS cloud] synth ok attempt="
        + attempt
        + "/"
        + maxAttempts
        + " elapsedMs="
        + elapsedMs
        + " inputLen="
        + inputLen
        + " bytes="
        + byteCount
        + " genId="
        + orDash(genId);
  }

  static String retry(String reason, int attempt, int maxAttempts, long elapsedMs) {
    return "[TTS cloud] synth retry reason="
        + reason
        + " attempt="
        + attempt
        + "/"
        + maxAttempts
        + " elapsedMs="
        + elapsedMs;
  }

  static String failure(
      String reason,
      int attempt,
      int maxAttempts,
      long elapsedMs,
      int inputLen,
      int httpStatus,
      String contentType,
      String genId,
      int byteCount,
      String detail) {
    return "[TTS cloud] synth fail reason="
        + reason
        + " attempt="
        + attempt
        + "/"
        + maxAttempts
        + " elapsedMs="
        + elapsedMs
        + " inputLen="
        + inputLen
        + " http="
        + (httpStatus <= 0 ? "-" : Integer.toString(httpStatus))
        + " contentType="
        + orDash(contentType)
        + " genId="
        + orDash(genId)
        + " bytes="
        + byteCount
        + " detail="
        + orDash(detail);
  }

  private static String orDash(String value) {
    return value == null || value.isEmpty() ? "-" : value;
  }
}
