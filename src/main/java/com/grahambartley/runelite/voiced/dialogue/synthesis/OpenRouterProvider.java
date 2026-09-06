package com.grahambartley.runelite.voiced.dialogue.synthesis;

import com.google.gson.JsonObject;
import okhttp3.Request;

/**
 * OpenRouter request conventions shared by every OpenRouter call (TTS and translation): the {@code
 * provider} preferences block, and the authenticated, app-attributed request headers, so routing
 * and dashboard attribution behave identically across call sites.
 *
 * <p>{@code sort: "throughput"} routes each request to the lowest-latency provider for the model
 * (the same effect as the {@code :nitro} model-slug shortcut, kept in the body rather than mangling
 * the fixed model id).
 */
final class OpenRouterProvider {

  /** Routes to the fastest provider for the model (equivalent to the {@code :nitro} suffix). */
  static final String THROUGHPUT_SORT = "throughput";

  /** OpenRouter app-attribution headers, shown as the app name/URL in its usage dashboard. */
  static final String APP_TITLE = "RuneLite Voiced Dialogue";

  static final String APP_URL = "https://github.com/grabartley/runelite-voiced-dialogue";

  private OpenRouterProvider() {}

  /** Attaches the {@code provider} preferences to a request body, pinning throughput routing. */
  static void apply(JsonObject body) {
    JsonObject provider = new JsonObject();
    provider.addProperty("sort", THROUGHPUT_SORT);
    body.add("provider", provider);
  }

  /** A request builder carrying the bearer key and the app-attribution headers. */
  static Request.Builder attributedRequest(String url, String apiKey) {
    return new Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer " + apiKey)
        .addHeader("User-Agent", CloudHttp.USER_AGENT)
        .addHeader("HTTP-Referer", APP_URL)
        .addHeader("X-Title", APP_TITLE);
  }
}
