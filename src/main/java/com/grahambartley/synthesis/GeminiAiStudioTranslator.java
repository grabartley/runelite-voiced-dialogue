package com.grahambartley.synthesis;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grahambartley.VoicedDialogueConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Translates a dialogue line into the configured spoken language before it is voiced, via the
 * Gemini API's {@code generateContent} endpoint. The direct-to-Google counterpart of {@link
 * OpenRouterTranslator}: same role in the pipeline, same {@link
 * OpenRouterTranslator#systemPrompt(String)} (kept shared so the two providers rewrite lines
 * identically and their prompt caches key the same way), differing only in the request/response
 * shape and the {@code x-goog-api-key} authentication.
 *
 * <p>Every failure path returns {@code null} so {@link GeminiAiStudioTtsBackend} fails the line
 * gracefully rather than voicing the wrong language or caching a mistranslation.
 */
@Slf4j
final class GeminiAiStudioTranslator {

  /** The Gemini API name of the same Flash Lite model the OpenRouter translation hop uses. */
  static final String MODEL = "gemini-3.1-flash-lite";

  static final String PRODUCTION_ENDPOINT =
      "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";

  private static final String USER_AGENT = "runelite-voiced-dialogue";

  private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

  private final OkHttpClient httpClient;
  private final VoicedDialogueConfig config;
  private final Gson gson;
  private final String endpoint;

  /** Test seam: points the translation request at a mock server instead of the live host. */
  GeminiAiStudioTranslator(
      OkHttpClient httpClient, VoicedDialogueConfig config, Gson gson, String endpoint) {
    this.httpClient = httpClient;
    this.config = config;
    this.gson = gson;
    this.endpoint = endpoint;
  }

  /**
   * Returns {@code text} translated into {@code language}, or {@code null} on any failure (non-2xx,
   * network error, empty/unparseable body). The caller treats {@code null} as a failed line rather
   * than voicing untranslated text under a target-language cache key.
   */
  String translate(String text, String language, String apiKey) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    JsonObject systemInstruction = new JsonObject();
    systemInstruction.add("parts", parts(OpenRouterTranslator.systemPrompt(language)));
    JsonObject content = new JsonObject();
    content.add("parts", parts(text));
    JsonArray contents = new JsonArray();
    contents.add(content);
    JsonObject payload = new JsonObject();
    payload.add("systemInstruction", systemInstruction);
    payload.add("contents", contents);

    Request httpRequest =
        new Request.Builder()
            .url(endpoint)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("User-Agent", USER_AGENT)
            .post(
                RequestBody.create(
                    JSON_MEDIA_TYPE, gson.toJson(payload).getBytes(StandardCharsets.UTF_8)))
            .build();

    long start = System.nanoTime();
    try (Response response = httpClient.newCall(httpRequest).execute()) {
      ResponseBody body = response.body();
      String raw = body == null ? "" : body.string();
      long elapsedMs = elapsedMs(start);
      if (!response.isSuccessful()) {
        log.warn(
            "[TTS cloud] translate fail reason=non-2xx http={} elapsedMs={} inLen={} detail={}",
            response.code(),
            elapsedMs,
            text.length(),
            response.message());
        return null;
      }
      String translated = extractText(raw);
      if (translated == null || translated.isEmpty()) {
        log.warn(
            "[TTS cloud] translate fail reason=no-content http={} elapsedMs={} inLen={}",
            response.code(),
            elapsedMs,
            text.length());
        return null;
      }
      if (config.debugMode()) {
        log.info(
            "[TTS cloud] translate ok lang={} elapsedMs={} inLen={} outLen={} -> \"{}\"",
            language,
            elapsedMs,
            text.length(),
            translated.length(),
            translated);
      }
      return translated;
    } catch (IOException | RuntimeException e) {
      log.warn(
          "[TTS cloud] translate fail reason=error elapsedMs={} inLen={} detail={}",
          elapsedMs(start),
          text.length(),
          e.getMessage());
      return null;
    }
  }

  private static JsonArray parts(String text) {
    JsonObject part = new JsonObject();
    part.addProperty("text", text);
    JsonArray parts = new JsonArray();
    parts.add(part);
    return parts;
  }

  /** Concatenates {@code candidates[0].content.parts[].text} out of a Gemini response, trimmed. */
  private String extractText(String raw) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    try {
      JsonObject parsed = gson.fromJson(raw, JsonObject.class);
      JsonArray candidates = parsed == null ? null : parsed.getAsJsonArray("candidates");
      if (candidates == null || candidates.size() == 0) {
        return null;
      }
      JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
      JsonArray parts = content == null ? null : content.getAsJsonArray("parts");
      if (parts == null) {
        return null;
      }
      StringBuilder translated = new StringBuilder();
      for (JsonElement element : parts) {
        JsonObject part = element.getAsJsonObject();
        if (part.has("text")) {
          translated.append(part.get("text").getAsString());
        }
      }
      String result = translated.toString().trim();
      return result.isEmpty() ? null : result;
    } catch (RuntimeException e) {
      log.debug("[TTS cloud] translation response parse error: {}", e.getMessage());
      return null;
    }
  }

  /** Elapsed wall-clock since {@code startNanos}, in whole milliseconds, for a latency trace. */
  private static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }
}
