package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.audio.TestPcm;
import java.util.Base64;
import java.util.List;

/** The Gemini API response documents the Google AI Studio tests serve from their mock server. */
final class AiStudioResponses {

  private AiStudioResponses() {}

  /** A complete response carrying the samples as one base64 inlineData part. */
  static String audio(short[] samples) {
    return audioDocument(TestPcm.raw(samples), "STOP");
  }

  /** A complete response whose usageMetadata reports what the API metered for the call. */
  static String audioWithUsage(short[] samples, long audioTokens, long textTokens) {
    return withUsage(audio(samples), audioTokens, textTokens);
  }

  /** Folds a usageMetadata block into an existing response document. */
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

  /** One response document carrying the raw audio bytes, and a finish reason when it ends. */
  static String audioDocument(byte[] audioBytes, String finishReason) {
    JsonObject inlineData = new JsonObject();
    inlineData.addProperty("mimeType", "audio/L16;codec=pcm;rate=24000");
    inlineData.addProperty("data", Base64.getEncoder().encodeToString(audioBytes));
    JsonObject part = new JsonObject();
    part.add("inlineData", inlineData);
    return candidates(part, finishReason);
  }

  /** One SSE event per audio chunk, the last carrying the finish reason. */
  static String sse(List<byte[]> chunks, String finishReason) {
    StringBuilder sse = new StringBuilder();
    for (int i = 0; i < chunks.size(); i++) {
      String reason = i == chunks.size() - 1 ? finishReason : null;
      sse.append("data: ").append(audioDocument(chunks.get(i), reason)).append("\n\n");
    }
    return sse.toString();
  }

  /** A complete response from the translation model, whose single part is text. */
  static String translation(String content) {
    JsonObject textPart = new JsonObject();
    textPart.addProperty("text", content);
    return candidates(textPart, "STOP");
  }

  /** The single-candidate, single-part envelope every response above shares. */
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
