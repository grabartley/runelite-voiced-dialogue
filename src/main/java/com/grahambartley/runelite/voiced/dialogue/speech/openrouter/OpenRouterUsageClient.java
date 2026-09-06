package com.grahambartley.runelite.voiced.dialogue.speech.openrouter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Reads how many credits an OpenRouter key has spent, straight from OpenRouter.
 *
 * <p>{@code GET /api/v1/key} reports the key's all-time {@code usage}, the same figure the account
 * dashboard bills against. Two reads (one at session start, one when the player asks) turn that
 * into a real spend for the session, so the {@code ::voicedspend} readout quotes what OpenRouter
 * actually charged rather than a modelled guess.
 *
 * <p>Every failure path returns {@code null}: an unreadable balance leaves the readout saying so,
 * which is honest, rather than silently substituting an estimate. Runs off the game thread only.
 */
@Slf4j
public final class OpenRouterUsageClient {

  static final String PRODUCTION_ENDPOINT = "https://openrouter.ai/api/v1/key";

  private final OkHttpClient httpClient;
  private final Gson gson;
  private final String endpoint;

  public OpenRouterUsageClient(OkHttpClient httpClient, Gson gson) {
    this(httpClient, gson, PRODUCTION_ENDPOINT);
  }

  /** Test seam: points the balance read at a mock server instead of the live host. */
  OpenRouterUsageClient(OkHttpClient httpClient, Gson gson, String endpoint) {
    this.httpClient = httpClient;
    this.gson = gson;
    this.endpoint = endpoint;
  }

  /**
   * The key's all-time credit usage, or {@code null} when it cannot be read (no key, non-2xx,
   * network error, or a body without a numeric {@code usage}).
   */
  public Double fetchUsage(String apiKey) {
    if (!CloudHttp.isNonBlank(apiKey)) {
      return null;
    }
    Request request =
        new Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer " + apiKey.trim())
            .addHeader("User-Agent", CloudHttp.USER_AGENT)
            .get()
            .build();
    try (Response response = httpClient.newCall(request).execute()) {
      ResponseBody body = response.body();
      String raw = body == null ? "" : body.string();
      if (!response.isSuccessful()) {
        log.debug("[TTS spend] balance read HTTP {}", response.code());
        return null;
      }
      return extractUsage(raw);
    } catch (IOException | RuntimeException e) {
      log.debug("[TTS spend] balance read failed: {}", e.getMessage());
      return null;
    }
  }

  /** Pulls {@code data.usage} out of a key response, or {@code null} when it is absent. */
  Double extractUsage(String raw) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    try {
      JsonObject parsed = gson.fromJson(raw, JsonObject.class);
      if (parsed == null || !parsed.has("data")) {
        return null;
      }
      JsonObject data = parsed.getAsJsonObject("data");
      if (data == null || !data.has("usage") || data.get("usage").isJsonNull()) {
        return null;
      }
      return data.get("usage").getAsDouble();
    } catch (RuntimeException e) {
      log.debug("[TTS spend] balance response parse error: {}", e.getMessage());
      return null;
    }
  }
}
