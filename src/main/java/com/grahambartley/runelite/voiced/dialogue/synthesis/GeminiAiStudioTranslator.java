package com.grahambartley.runelite.voiced.dialogue.synthesis;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Translates a dialogue line into the configured spoken language before it is voiced, via the
 * Gemini API's {@code generateContent} endpoint. The direct-to-Google counterpart of {@link
 * OpenRouterTranslator}: same role in the pipeline, same {@link
 * CloudTtsText#translatorSystemPrompt(String)} (kept shared so the two providers rewrite lines
 * identically and their prompt caches key the same way), differing only in the request/response
 * shape and the {@code x-goog-api-key} authentication.
 *
 * <p>Every failure path returns {@code null} so {@link GeminiAiStudioTtsBackend} fails the line
 * gracefully rather than voicing the wrong language or caching a mistranslation.
 */
@Slf4j
final class GeminiAiStudioTranslator implements CloudTranslatorCall.Ops {

  /** The Gemini API name of the Flash Lite translation model, shared with the OpenRouter hop. */
  static final String MODEL = "gemini-3.1-flash-lite";

  static final String PRODUCTION_ENDPOINT =
      "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";

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

  /** A completed translation plus the tokens the API reported metering for it. */
  static final class Translation {
    final String text;
    final GeminiTokenUsage usage;

    Translation(String text, GeminiTokenUsage usage) {
      this.text = text;
      this.usage = usage;
    }
  }

  /**
   * Returns {@code text} translated into {@code language} with the tokens the hop metered, or
   * {@code null} on any failure (non-2xx, network error, empty/unparseable body). The caller treats
   * {@code null} as a failed line rather than voicing untranslated text under a target-language
   * cache key.
   */
  Translation translate(String text, String language, String apiKey) {
    if (text == null || text.isEmpty()) {
      // Same shape as the OpenRouter translator's guard: null in, null out; empty in, empty out.
      return text == null ? null : new Translation(text, GeminiTokenUsage.NONE);
    }
    CloudTranslatorCall.Outcome outcome =
        CloudTranslatorCall.run(httpClient, config, this, text, language, apiKey);
    if (outcome == null) {
      return null;
    }
    // The hop is its own billable call against its own model, so its metered tokens ride back
    // with the text rather than being lost to the session's cost.
    return new Translation(outcome.text, GeminiTokenUsage.forText(gson, outcome.raw));
  }

  @Override
  public Request buildRequest(String text, String language, String apiKey) {
    JsonObject systemInstruction = new JsonObject();
    systemInstruction.add("parts", parts(CloudTtsText.translatorSystemPrompt(language)));
    JsonObject content = new JsonObject();
    content.add("parts", parts(text));
    JsonArray contents = new JsonArray();
    contents.add(content);
    JsonObject payload = new JsonObject();
    payload.add("systemInstruction", systemInstruction);
    payload.add("contents", contents);

    return new Request.Builder()
        .url(endpoint)
        .addHeader("x-goog-api-key", apiKey)
        .addHeader("User-Agent", CloudHttp.USER_AGENT)
        .post(
            RequestBody.create(
                CloudHttp.JSON_MEDIA_TYPE, gson.toJson(payload).getBytes(StandardCharsets.UTF_8)))
        .build();
  }

  private static JsonArray parts(String text) {
    JsonObject part = new JsonObject();
    part.addProperty("text", text);
    JsonArray parts = new JsonArray();
    parts.add(part);
    return parts;
  }

  /** Concatenates {@code candidates[0].content.parts[].text} out of a Gemini response, trimmed. */
  @Override
  public String extractText(String raw) {
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
}
