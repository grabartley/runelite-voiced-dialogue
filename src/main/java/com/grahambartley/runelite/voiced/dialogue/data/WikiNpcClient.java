package com.grahambartley.runelite.voiced.dialogue.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.voice.RaceBucket;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Looks an NPC's race, gender and ethnicity up on the Old School RuneScape Wiki at runtime, for
 * NPCs missing from the bundled table (typically ones added to the game since the last plugin
 * update). It queries the MediaWiki API for the NPC's page lead wikitext and parses the {@code
 * Infobox NPC} fields, mirroring the offline generator ({@code tools/generate_npc_voices.py}) so a
 * learned NPC sounds the same as a baked-in one.
 *
 * <p>This runs only on a background thread (never the game thread), through the injected {@link
 * OkHttpClient}. Every failure path (network error, missing page, no infobox, unparsable body)
 * returns {@code null} rather than throwing, so a lookup miss simply leaves the NPC on the default
 * voice.
 */
@Slf4j
public final class WikiNpcClient {

  private static final String PRODUCTION_API = "https://oldschool.runescape.wiki/api.php";
  private static final String USER_AGENT = "runelite-voiced-dialogue";
  private static final Duration CALL_TIMEOUT = Duration.ofSeconds(8);

  private static final Pattern RACE = field("race");
  private static final Pattern GENDER = field("gender");
  private static final Pattern LEAGUE_REGION = field("leagueRegion");
  private static final Pattern LOCATION = field("location");
  private static final Pattern MENAPHITE =
      Pattern.compile("sophanem|menaphos|menaphite|necropolis", Pattern.CASE_INSENSITIVE);
  private static final Pattern REF_TAG = Pattern.compile("<ref[^>]*>.*?</ref>");
  private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
  private static final Pattern TEMPLATE = Pattern.compile("\\{\\{[^}]*\\}\\}");

  private static Pattern field(String key) {
    return Pattern.compile("\\|\\s*" + key + "\\d*\\s*=\\s*([^\\n|]+)", Pattern.CASE_INSENSITIVE);
  }

  private final OkHttpClient httpClient;
  private final String api;

  public WikiNpcClient(OkHttpClient httpClient) {
    this(httpClient, PRODUCTION_API);
  }

  WikiNpcClient(OkHttpClient httpClient, String api) {
    this.httpClient = httpClient.newBuilder().callTimeout(CALL_TIMEOUT).build();
    this.api = api;
  }

  /**
   * Resolves race/gender/ethnicity for an NPC by wiki page name, or {@code null} when it cannot be
   * found or parsed. The returned attributes carry source {@code "Wiki"}.
   */
  public NpcAttributes lookup(String npcName) {
    if (npcName == null || npcName.trim().isEmpty()) {
      return null;
    }
    HttpUrl url =
        HttpUrl.get(api)
            .newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("prop", "revisions")
            .addQueryParameter("rvprop", "content")
            .addQueryParameter("rvslots", "main")
            .addQueryParameter("rvsection", "0")
            .addQueryParameter("redirects", "1")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("titles", npcName.trim())
            .build();
    Request request =
        new Request.Builder().url(url).addHeader("User-Agent", USER_AGENT).get().build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        return null;
      }
      ResponseBody body = response.body();
      String wikitext = extractWikitext(body == null ? null : body.string());
      if (wikitext == null) {
        return null;
      }
      String race = bucketForRace(firstField(RACE, wikitext));
      if (race == null) {
        return null; // no Infobox NPC race -> not a usable NPC page (disambiguation, monster, ...)
      }
      String gender = normaliseGender(firstField(GENDER, wikitext));
      String ethnicity =
          ethnicityKey(firstField(LEAGUE_REGION, wikitext), firstField(LOCATION, wikitext));
      NpcAttributes attributes = new NpcAttributes(race, gender, AttributeSource.WIKI);
      attributes.setEthnicity(ethnicity);
      return attributes;
    } catch (Exception e) {
      log.debug("Wiki lookup for '{}' failed: {}", npcName, e.getMessage());
      return null;
    }
  }

  private String extractWikitext(String json) {
    if (json == null) {
      return null;
    }
    try {
      JsonObject root = new JsonParser().parse(json).getAsJsonObject();
      JsonArray pages = root.getAsJsonObject("query").getAsJsonArray("pages");
      if (pages.size() == 0) {
        return null;
      }
      JsonObject page = pages.get(0).getAsJsonObject();
      if (!page.has("revisions")) {
        return null;
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

  private static String firstField(Pattern pattern, String wikitext) {
    Matcher m = pattern.matcher(wikitext);
    if (!m.find()) {
      return null;
    }
    String value = m.group(1);
    value = REF_TAG.matcher(value).replaceAll("");
    value = HTML_TAG.matcher(value).replaceAll("");
    value = value.replace("[[", "").replace("]]", "");
    value = TEMPLATE.matcher(value).replaceAll("");
    return value.trim();
  }

  /**
   * Maps wiki race text onto the voice bucket name stored in the tables. Anything the shared {@link
   * RaceBucket} table does not recognise is a person until proven otherwise, so an obscure race
   * still gets a human voice rather than none.
   */
  static String bucketForRace(String raceText) {
    if (raceText == null || raceText.isEmpty()) {
      return null;
    }
    RaceBucket bucket = RaceBucket.forWikiText(raceText);
    return bucket == null ? RaceBucket.HUMAN.bucketName() : bucket.bucketName();
  }

  private static String normaliseGender(String genderText) {
    if (genderText != null) {
      String g = genderText.trim().toLowerCase(Locale.ROOT);
      if (g.startsWith("f")) {
        return "Female";
      }
      if (g.startsWith("m")) {
        return "Male";
      }
    }
    return "Male";
  }

  /** Maps a single wiki leagueRegion onto an ethnicity accent key; mirrors the generator. */
  static String ethnicityKey(String leagueRegion, String location) {
    if (leagueRegion == null) {
      return null;
    }
    String lr = leagueRegion.trim();
    if (lr.contains(",") || lr.contains("&")) {
      return null; // documented in several regions -> no single home accent
    }
    switch (lr.toLowerCase(Locale.ROOT)) {
      case "desert":
        return location != null && MENAPHITE.matcher(location).find() ? "menaphite" : "kharidian";
      case "misthalin":
        return "misthalin";
      case "asgarnia":
        return "asgarnia";
      case "kandarin":
        return "kandarin";
      case "kourend":
        return "kourend";
      case "wilderness":
        return "wilderness";
      case "tirannwn":
        return "tirannwn";
      case "varlamore":
        return "varlamore";
      case "karamja":
        return "karamja";
      case "morytania":
        return "morytania";
      case "fremennik":
        return "fremennik";
      default:
        return null;
    }
  }
}
