package com.grahambartley.runelite.voiced.dialogue.synthesis;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Translates a dialogue line into the configured spoken language before it is voiced, via
 * OpenRouter's chat-completions endpoint and the Gemini Flash Lite model.
 *
 * <p>This is the optional first hop of the cloud pipeline: {@link OpenRouterTtsBackend} calls it
 * only when {@link VoicedDialogueConfig#cloudLanguage()} is not English, so the common case pays no
 * extra request. The system prompt is the shared {@link
 * CloudTtsText#translatorSystemPrompt(String)}: fixed per language (all per-line variance lives in
 * the user message) so the cacheable prefix is byte-identical across calls and the model's implicit
 * prompt cache hits. Every failure path returns {@code null} so the backend fails the line
 * gracefully rather than voicing the wrong language or caching a mistranslation.
 */
@Slf4j
final class OpenRouterTranslator implements CloudTranslatorCall.Ops {

  /**
   * The lightweight model used for the translation hop: fast and cheap relative to the TTS call.
   * The same Flash Lite model the direct Gemini hop uses, under OpenRouter's namespace.
   */
  static final String MODEL = "google/" + GeminiAiStudioTranslator.MODEL;

  private final OkHttpClient httpClient;
  private final VoicedDialogueConfig config;
  private final Gson gson;
  private final String endpoint;

  /** Test seam: points the translation request at a mock server instead of the live host. */
  OpenRouterTranslator(
      OkHttpClient httpClient, VoicedDialogueConfig config, Gson gson, String endpoint) {
    this.httpClient = httpClient;
    this.config = config;
    this.gson = gson;
    this.endpoint = endpoint;
  }

  /**
   * Returns {@code text} translated into {@code language}, or {@code null} on any failure (missing
   * key, non-2xx, network error, empty/unparseable body). The caller treats {@code null} as a
   * failed line rather than voicing untranslated text under a target-language cache key.
   */
  String translate(String text, String language, String apiKey) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    CloudTranslatorCall.Outcome outcome =
        CloudTranslatorCall.run(httpClient, config, this, text, language, apiKey);
    return outcome == null ? null : outcome.text;
  }

  @Override
  public Request buildRequest(String text, String language, String apiKey) {
    JsonObject payload = new JsonObject();
    payload.addProperty("model", MODEL);
    JsonArray messages = new JsonArray();
    messages.add(message("system", CloudTtsText.translatorSystemPrompt(language)));
    messages.add(message("user", text));
    payload.add("messages", messages);
    OpenRouterProvider.apply(payload);

    return OpenRouterProvider.attributedRequest(endpoint, apiKey)
        .post(
            RequestBody.create(
                CloudHttp.JSON_MEDIA_TYPE, gson.toJson(payload).getBytes(StandardCharsets.UTF_8)))
        .build();
  }

  private static JsonObject message(String role, String content) {
    JsonObject message = new JsonObject();
    message.addProperty("role", role);
    message.addProperty("content", content);
    return message;
  }

  /** Pulls {@code choices[0].message.content} out of a chat-completions response, trimmed. */
  @Override
  public String extractText(String raw) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    try {
      JsonObject parsed = gson.fromJson(raw, JsonObject.class);
      if (parsed == null || !parsed.has("choices")) {
        return null;
      }
      JsonArray choices = parsed.getAsJsonArray("choices");
      if (choices.size() == 0) {
        return null;
      }
      JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
      if (message == null || !message.has("content")) {
        return null;
      }
      return message.get("content").getAsString().trim();
    } catch (RuntimeException e) {
      log.debug("[TTS cloud] translation response parse error: {}", e.getMessage());
      return null;
    }
  }
}
