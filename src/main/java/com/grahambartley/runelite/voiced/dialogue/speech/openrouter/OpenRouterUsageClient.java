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

@Slf4j
public final class OpenRouterUsageClient {

  static final String PRODUCTION_ENDPOINT = "https://openrouter.ai/api/v1/key";

  private final OkHttpClient httpClient;
  private final Gson gson;
  private final String endpoint;

  public OpenRouterUsageClient(OkHttpClient httpClient, Gson gson) {
    this(httpClient, gson, PRODUCTION_ENDPOINT);
  }

  OpenRouterUsageClient(OkHttpClient httpClient, Gson gson, String endpoint) {
    this.httpClient = httpClient;
    this.gson = gson;
    this.endpoint = endpoint;
  }

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
