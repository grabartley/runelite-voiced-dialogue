package com.grahambartley.runelite.voiced.dialogue.speech.openrouter;

import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp;
import okhttp3.Request;

final class OpenRouterProvider {

  static final String THROUGHPUT_SORT = "throughput";

  static final String APP_TITLE = "RuneLite Voiced Dialogue";

  static final String APP_URL = "https://github.com/grabartley/runelite-voiced-dialogue";

  private OpenRouterProvider() {}

  static void apply(JsonObject body) {
    JsonObject provider = new JsonObject();
    provider.addProperty("sort", THROUGHPUT_SORT);
    body.add("provider", provider);
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
