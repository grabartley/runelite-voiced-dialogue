package com.grahambartley.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.grahambartley.dialogue.WikiTranscript;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Fetches and parses an NPC's {@code Transcript:<name>} page from the OSRS Wiki. */
@Slf4j
public final class WikiTranscriptClient {

  private static final String PRODUCTION_API = "https://oldschool.runescape.wiki/api.php";
  private static final Duration CALL_TIMEOUT = Duration.ofSeconds(8);

  private final OkHttpClient httpClient;
  private final Gson gson;
  private final String api;

  public WikiTranscriptClient(OkHttpClient httpClient, Gson gson) {
    this(httpClient, gson, PRODUCTION_API);
  }

  WikiTranscriptClient(OkHttpClient httpClient, Gson gson, String api) {
    this.httpClient = httpClient.newBuilder().callTimeout(CALL_TIMEOUT).build();
    this.gson = gson;
    this.api = api;
  }

  public WikiTranscript lookup(String npcName) {
    if (npcName == null || npcName.trim().isEmpty() || "Unknown NPC".equals(npcName)) {
      return null;
    }
    HttpUrl url =
        HttpUrl.get(api)
            .newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("prop", "revisions")
            .addQueryParameter("rvprop", "content")
            .addQueryParameter("rvslots", "main")
            .addQueryParameter("redirects", "1")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("titles", "Transcript:" + npcName.trim())
            .build();
    Request request =
        new Request.Builder()
            .url(url)
            .addHeader("User-Agent", "runelite-voiced-dialogue")
            .get()
            .build();
    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        return null;
      }
      ResponseBody body = response.body();
      String wikitext = extractWikitext(body == null ? null : body.string());
      if (wikitext == null) {
        return null;
      }
      log.debug("[TTS predictive] wiki transcript npc='{}' found={}", npcName, !wikitext.isEmpty());
      return WikiTranscript.parse(wikitext, npcName);
    } catch (Exception e) {
      log.debug("Wiki transcript lookup for '{}' failed: {}", npcName, e.getMessage());
      return null;
    }
  }

  private String extractWikitext(String json) {
    if (json == null) {
      return null;
    }
    try {
      JsonObject root = gson.fromJson(json, JsonObject.class);
      JsonArray pages = root.getAsJsonObject("query").getAsJsonArray("pages");
      JsonObject page = pages.get(0).getAsJsonObject();
      if (!page.has("revisions")) {
        return "";
      }
      return page.getAsJsonArray("revisions")
          .get(0)
          .getAsJsonObject()
          .getAsJsonObject("slots")
          .getAsJsonObject("main")
          .get("content")
          .getAsString();
    } catch (RuntimeException e) {
      return null;
    }
  }
}
