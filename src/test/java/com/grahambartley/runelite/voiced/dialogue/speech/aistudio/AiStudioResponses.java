package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.audio.TestPcm;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp;
import java.net.HttpURLConnection;
import java.util.Base64;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;

final class AiStudioResponses {

  static final String DAILY_CAP_RETRY_DELAY = "2917s";

  private AiStudioResponses() {}

  static MockResponse ok(String body) {
    return new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody(body);
  }

  static MockResponse tooManyRequests(String body) {
    return new MockResponse().setResponseCode(CloudHttp.HTTP_TOO_MANY_REQUESTS).setBody(body);
  }

  static MockResponse quotaRejection() {
    return tooManyRequests(dailyCapExhausted());
  }

  static String audio(short[] samples) {
    return audioDocument(TestPcm.raw(samples), "STOP");
  }

  static String audioWithUsage(short[] samples, long audioTokens, long textTokens) {
    return withUsage(audio(samples), audioTokens, textTokens);
  }

  static String withUsage(String document, long audioTokens, long textTokens) {
    JsonObject detail = new JsonObject();
    detail.addProperty("modality", "AUDIO");
    detail.addProperty("tokenCount", audioTokens);
    JsonArray details = new JsonArray();
    details.add(detail);
    JsonObject usage = new JsonObject();
    usage.addProperty("promptTokenCount", textTokens);
    usage.addProperty("candidatesTokenCount", audioTokens);
    usage.add("candidatesTokensDetails", details);
    JsonObject body = new JsonParser().parse(document).getAsJsonObject();
    body.add("usageMetadata", usage);
    return body.toString();
  }

  static String audioDocument(byte[] audioBytes, String finishReason) {
    JsonObject inlineData = new JsonObject();
    inlineData.addProperty("mimeType", "audio/L16;codec=pcm;rate=24000");
    inlineData.addProperty("data", Base64.getEncoder().encodeToString(audioBytes));
    JsonObject part = new JsonObject();
    part.add("inlineData", inlineData);
    return candidates(part, finishReason);
  }

  static String sse(List<byte[]> chunks, String finishReason) {
    StringBuilder sse = new StringBuilder();
    for (int i = 0; i < chunks.size(); i++) {
      String reason = i == chunks.size() - 1 ? finishReason : null;
      sse.append("data: ").append(audioDocument(chunks.get(i), reason)).append("\n\n");
    }
    return sse.toString();
  }

  static String translation(String content) {
    JsonObject textPart = new JsonObject();
    textPart.addProperty("text", content);
    return candidates(textPart, "STOP");
  }

  static String quotaFailure(String quotaId, String quotaMetric, String quotaValue, String model) {
    JsonObject violation = new JsonObject();
    addIfPresent(violation, "quotaId", quotaId);
    addIfPresent(violation, "quotaMetric", quotaMetric);
    addIfPresent(violation, "quotaValue", quotaValue);
    if (!model.isEmpty()) {
      JsonObject dimensions = new JsonObject();
      dimensions.addProperty("model", model);
      dimensions.addProperty("location", "global");
      violation.add("quotaDimensions", dimensions);
    }
    JsonArray violations = new JsonArray();
    violations.add(violation);
    JsonObject quotaFailure = new JsonObject();
    quotaFailure.addProperty("@type", "type.googleapis.com/google.rpc.QuotaFailure");
    quotaFailure.add("violations", violations);
    JsonArray details = new JsonArray();
    details.add(quotaFailure);
    details.add(retryInfo(DAILY_CAP_RETRY_DELAY));
    return rejection(details);
  }

  static String dailyCapExhausted() {
    return quotaFailure(
        "GenerateRequestsPerDayPerProjectPerModel",
        "generativelanguage.googleapis.com/generate_requests_per_model_per_day",
        "100",
        "gemini-3.1-flash-tts");
  }

  static String statedRetryDelay(String retryDelay) {
    JsonArray details = new JsonArray();
    details.add(retryInfo(retryDelay));
    return rejection(details);
  }

  static String quotaExhausted() {
    return rejection(null);
  }

  private static JsonObject retryInfo(String retryDelay) {
    JsonObject retryInfo = new JsonObject();
    retryInfo.addProperty("@type", "type.googleapis.com/google.rpc.RetryInfo");
    retryInfo.addProperty("retryDelay", retryDelay);
    return retryInfo;
  }

  private static String rejection(JsonArray details) {
    JsonObject error = new JsonObject();
    error.addProperty("code", 429);
    error.addProperty("message", "Resource has been exhausted.");
    error.addProperty("status", "RESOURCE_EXHAUSTED");
    if (details != null) {
      error.add("details", details);
    }
    JsonObject body = new JsonObject();
    body.add("error", error);
    return body.toString();
  }

  private static void addIfPresent(JsonObject object, String field, String value) {
    if (!value.isEmpty()) {
      object.addProperty(field, value);
    }
  }

  private static String candidates(JsonObject part, String finishReason) {
    JsonArray parts = new JsonArray();
    parts.add(part);
    JsonObject content = new JsonObject();
    content.add("parts", parts);
    JsonObject candidate = new JsonObject();
    candidate.add("content", content);
    if (finishReason != null) {
      candidate.addProperty("finishReason", finishReason);
    }
    JsonArray candidates = new JsonArray();
    candidates.add(candidate);
    JsonObject body = new JsonObject();
    body.add("candidates", candidates);
    return body.toString();
  }
}
