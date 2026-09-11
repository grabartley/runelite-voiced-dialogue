package com.grahambartley.runelite.voiced.dialogue.speech.openrouter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
final class OpenRouterTranslator implements CloudTranslatorCall.Ops {

  static final String MODEL = GeminiTranslationModel.MODEL_ID;

  private final OkHttpClient httpClient;
  private final VoicedDialogueConfig config;
  private final Gson gson;
  private final String endpoint;

  OpenRouterTranslator(
      OkHttpClient httpClient, VoicedDialogueConfig config, Gson gson, String endpoint) {
    this.httpClient = httpClient;
    this.config = config;
    this.gson = gson;
    this.endpoint = endpoint;
  }

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
