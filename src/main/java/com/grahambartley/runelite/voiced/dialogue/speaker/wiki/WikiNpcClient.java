package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.speaker.AttributeSource;
import com.grahambartley.runelite.voiced.dialogue.speaker.NameNormalizer;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
public final class WikiNpcClient {

  private static final String PRODUCTION_API = "https://oldschool.runescape.wiki/api.php";
  private static final String USER_AGENT = "runelite-voiced-dialogue";
  private static final Duration CALL_TIMEOUT = Duration.ofSeconds(8);
  private static final String CATEGORY_LIMIT = "500";

  private final OkHttpClient httpClient;
  private final String api;
  private final WikiMapping mapping = WikiMapping.get();

  public WikiNpcClient(OkHttpClient httpClient) {
    this(httpClient, PRODUCTION_API);
  }

  WikiNpcClient(OkHttpClient httpClient, String api) {
    this.httpClient = httpClient.newBuilder().callTimeout(CALL_TIMEOUT).build();
    this.api = api;
  }

  public WikiLookup lookup(int npcId, String npcName) {
    String title = NameNormalizer.normalize(npcName);
    if (title.isEmpty()) {
      return WikiLookup.undocumented();
    }
    HttpUrl url =
        HttpUrl.get(api)
            .newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("prop", "revisions|categories")
            .addQueryParameter("rvprop", "content")
            .addQueryParameter("rvslots", "main")
            .addQueryParameter("rvsection", "0")
            .addQueryParameter("cllimit", CATEGORY_LIMIT)
            .addQueryParameter("redirects", "1")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("titles", title)
            .build();
    Request request =
        new Request.Builder().url(url).addHeader("User-Agent", USER_AGENT).get().build();

    String body;
    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        log.debug("Wiki lookup for '{}' answered {}", title, response.code());
        return WikiLookup.unreachable();
      }
      ResponseBody payload = response.body();
      body = payload == null ? null : payload.string();
    } catch (Exception e) {
      log.debug("Wiki lookup for '{}' failed: {}", title, e.getMessage());
      return WikiLookup.unreachable();
    }

    JsonObject page = firstPage(body);
    if (page == null) {
      return WikiLookup.undocumented();
    }
    String wikitext = wikitextOf(page);
    if (wikitext == null || !mapping.isNpcPage(wikitext)) {
      return WikiLookup.undocumented();
    }
    return WikiLookup.of(attributesFrom(npcId, WikiInfobox.parse(wikitext, categoriesOf(page))));
  }

  private NpcAttributes attributesFrom(int npcId, WikiInfobox infobox) {
    String race = mapping.raceForWikiText(infobox.race());
    if (race == null) {
      race = mapping.raceForCategories(infobox.categories());
    }
    if (race == null) {
      race = mapping.defaultRace();
    }
    NpcAttributes attributes =
        new NpcAttributes(race, gender(npcId, infobox), AttributeSource.WIKI);
    attributes.setNpcId(npcId);
    attributes.setEthnicity(
        mapping.ethnicityKey(infobox.leagueRegion(), infobox.location(), infobox.categories()));
    return attributes;
  }

  private String gender(int npcId, WikiInfobox infobox) {
    return mapping.genderForWikiText(infobox.genderForVersion(npcId));
  }

  private static JsonObject firstPage(String json) {
    if (json == null) {
      return null;
    }
    try {
      JsonArray pages =
          new JsonParser()
              .parse(json)
              .getAsJsonObject()
              .getAsJsonObject("query")
              .getAsJsonArray("pages");
      return pages.size() == 0 ? null : pages.get(0).getAsJsonObject();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String wikitextOf(JsonObject page) {
    try {
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

  private static List<String> categoriesOf(JsonObject page) {
    List<String> categories = new ArrayList<>();
    if (!page.has("categories") || !page.get("categories").isJsonArray()) {
      return categories;
    }
    for (JsonElement element : page.getAsJsonArray("categories")) {
      JsonObject category = element.getAsJsonObject();
      if (category.has("title")) {
        categories.add(category.get("title").getAsString());
      }
    }
    return categories;
  }
}
