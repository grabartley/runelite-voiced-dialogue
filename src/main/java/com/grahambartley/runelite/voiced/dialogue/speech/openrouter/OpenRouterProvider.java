package com.grahambartley.runelite.voiced.dialogue.speech.openrouter;

import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiTtsModel;
import okhttp3.Request;

final class OpenRouterProvider {

  static final String THROUGHPUT_SORT = "throughput";

  static final String GOOGLE_AI_STUDIO = "google-ai-studio";

  static final String APP_TITLE = "RuneLite Voiced Dialogue";

  static final String APP_URL = "https://github.com/grabartley/runelite-voiced-dialogue";

  private OpenRouterProvider() {}

  static void apply(JsonObject body) {
    body.add("provider", throughputProvider());
  }

  static void apply(JsonObject body, JsonObject speechMetadata) {
    JsonObject googleOptions = new JsonObject();
    googleOptions.add(GeminiTtsModel.SPEECH_METADATA, speechMetadata);
    JsonObject options = new JsonObject();
    options.add(GOOGLE_AI_STUDIO, googleOptions);
    JsonObject provider = throughputProvider();
    provider.add("options", options);
    body.add("provider", provider);
  }

  private static JsonObject throughputProvider() {
    JsonObject provider = new JsonObject();
    provider.addProperty("sort", THROUGHPUT_SORT);
    return provider;
  }

  static Request.Builder attributedRequest(String url, String apiKey) {
    return new Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer " + apiKey)
        .addHeader("User-Agent", CloudHttp.USER_AGENT)
        .addHeader("HTTP-Referer", APP_URL)
        .addHeader("X-Title", APP_TITLE);
  }
}
