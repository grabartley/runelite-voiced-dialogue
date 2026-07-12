package com.grahambartley.synthesis;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grahambartley.VoicedDialogueConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.IntConsumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Translates a dialogue line through the Gemini API before direct AI Studio synthesis. */
@Slf4j
final class GeminiAiStudioTranslator {

  static final String MODEL = "gemini-3.1-flash-lite";
  static final String PRODUCTION_ENDPOINT =
      "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";

  private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

  private final OkHttpClient httpClient;
  private final VoicedDialogueConfig config;
  private final Gson gson;
  private final String endpoint;
  private IntConsumer responseCodeListener = code -> {};

  GeminiAiStudioTranslator(OkHttpClient httpClient, VoicedDialogueConfig config, Gson gson) {
    this(httpClient, config, gson, PRODUCTION_ENDPOINT);
  }

  GeminiAiStudioTranslator(
      OkHttpClient httpClient, VoicedDialogueConfig config, Gson gson, String endpoint) {
    this.httpClient = httpClient;
    this.config = config;
    this.gson = gson;
    this.endpoint = endpoint;
  }

  void setResponseCodeListener(IntConsumer responseCodeListener) {
    this.responseCodeListener = responseCodeListener == null ? code -> {} : responseCodeListener;
  }

  /** Returns the translated text, or {@code null} when the provider cannot translate the line. */
  String translate(String text, String language, String apiKey) {
    return translate(text, language, apiKey, null, 1);
  }

  String translate(String text, String language, String apiKey, String context, int creativity) {
    if (text == null || text.isEmpty()) {
      return text;
    }

    JsonObject systemInstruction = new JsonObject();
    systemInstruction.add("parts", parts(OpenRouterTranslator.systemPrompt(language, creativity)));
    JsonObject content = new JsonObject();
    content.add("parts", parts(OpenRouterTranslator.contextualInput(text, context)));
    JsonArray contents = new JsonArray();
    contents.add(content);
    JsonObject payload = new JsonObject();
    payload.add("systemInstruction", systemInstruction);
    payload.add("contents", contents);

    Request request =
        new Request.Builder()
            .url(endpoint)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("User-Agent", "runelite-voiced-dialogue")
            .post(
                RequestBody.create(
                    JSON_MEDIA_TYPE, gson.toJson(payload).getBytes(StandardCharsets.UTF_8)))
            .build();
    try (Response response = httpClient.newCall(request).execute()) {
      ResponseBody body = response.body();
      String raw = body == null ? "" : body.string();
      responseCodeListener.accept(response.code());
      if (!response.isSuccessful()) {
        log.warn("[TTS AI Studio] translate failed with HTTP {}", response.code());
        return null;
      }
      String translated = extractText(raw);
      if (translated == null || translated.isEmpty()) {
        log.warn("[TTS AI Studio] translate response contained no text");
        return null;
      }
      if (config.debugMode()) {
        log.info("[TTS AI Studio] translated {} characters to {}", text.length(), language);
      }
      return translated;
    } catch (IOException | RuntimeException e) {
      log.warn("[TTS AI Studio] translate failed: {}", e.getMessage());
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

  private String extractText(String raw) {
    try {
      JsonObject response = gson.fromJson(raw, JsonObject.class);
      JsonArray candidates = response == null ? null : response.getAsJsonArray("candidates");
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
      log.debug("[TTS AI Studio] translation response parse error: {}", e.getMessage());
      return null;
    }
  }
}
