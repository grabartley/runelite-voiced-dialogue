package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudTranslatorCall;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudTtsText;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiTranslationModel;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

@Slf4j
final class AiStudioTranslator implements CloudTranslatorCall.Ops {

  static final String MODEL = GeminiTranslationModel.GEMINI_MODEL_ID;

  static final String PRODUCTION_ENDPOINT =
      "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";

  private final OkHttpClient httpClient;
  private final VoicedDialogueConfig config;
  private final Gson gson;
  private final String endpoint;

  AiStudioTranslator(
      OkHttpClient httpClient, VoicedDialogueConfig config, Gson gson, String endpoint) {
    this.httpClient = httpClient;
    this.config = config;
    this.gson = gson;
    this.endpoint = endpoint;
  }

  static final class Translation {
    final String text;
    final AiStudioTokenUsage usage;

    Translation(String text, AiStudioTokenUsage usage) {
      this.text = text;
      this.usage = usage;
    }
  }

  Translation translate(String text, String language, String apiKey) {
    if (text == null || text.isEmpty()) {
      return text == null ? null : new Translation(text, AiStudioTokenUsage.NONE);
    }
    CloudTranslatorCall.Outcome outcome =
        CloudTranslatorCall.run(httpClient, config, this, text, language, apiKey);
    if (outcome == null) {
      return null;
    }
    return new Translation(outcome.text, AiStudioTokenUsage.forText(gson, outcome.raw));
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
