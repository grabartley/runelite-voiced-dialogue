package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class WikiMapping {

  private static final String RESOURCE = "/wiki-mapping.json";

  private static final WikiMapping INSTANCE = load();

  private final List<RacePattern> raceRules;
  private final List<CategoryRule> categoryRules;
  private final Map<String, String> leagueRegionEthnicity;
  private final Pattern infoboxTemplates;
  private final String defaultRace;
  private final String defaultGender;
  private final String femaleGender;
  private final String desertLeagueRegion;
  private final Pattern menaphiteHint;
  private final String menaphiteEthnicity;
  private final String desertEthnicity;

  private WikiMapping(JsonObject root) {
    raceRules = new ArrayList<>();
    for (JsonElement element : root.getAsJsonArray("raceRules")) {
      JsonObject rule = element.getAsJsonObject();
      raceRules.add(
          new RacePattern(
              Pattern.compile(rule.get("pattern").getAsString(), Pattern.CASE_INSENSITIVE),
              rule.get("race").getAsString()));
    }
    categoryRules = new ArrayList<>();
    for (JsonElement element : root.getAsJsonArray("categoryRaceRules")) {
      JsonObject rule = element.getAsJsonObject();
      categoryRules.add(
          new CategoryRule(
              rule.get("keyword").getAsString().toLowerCase(Locale.ROOT),
              rule.get("race").getAsString()));
    }
    leagueRegionEthnicity = new HashMap<>();
    JsonObject regions = root.getAsJsonObject("leagueRegionEthnicity");
    for (String region : regions.keySet()) {
      leagueRegionEthnicity.put(region.toLowerCase(Locale.ROOT), regions.get(region).getAsString());
    }
    infoboxTemplates = infoboxPattern(root.getAsJsonArray("infoboxTemplates"));
    defaultRace = root.get("defaultRace").getAsString();
    defaultGender = root.get("defaultGender").getAsString();
    femaleGender = root.get("femaleGender").getAsString();
    JsonObject desert = root.getAsJsonObject("desert");
    desertLeagueRegion = desert.get("leagueRegion").getAsString().toLowerCase(Locale.ROOT);
    menaphiteHint = Pattern.compile(desert.get("hint").getAsString(), Pattern.CASE_INSENSITIVE);
    menaphiteEthnicity = desert.get("hinted").getAsString();
    desertEthnicity = desert.get("default").getAsString();
  }

  static WikiMapping get() {
    return INSTANCE;
  }

  String defaultRace() {
    return defaultRace;
  }

  String genderForWikiText(String genderText) {
    if (genderText != null) {
      String normalised = genderText.trim().toLowerCase(Locale.ROOT);
      if (normalised.startsWith("f")) {
        return femaleGender;
      }
    }
    return defaultGender;
  }

  String raceForWikiText(String raceText) {
    if (raceText == null || raceText.isEmpty()) {
      return null;
    }
    for (RacePattern rule : raceRules) {
      if (rule.pattern.matcher(raceText).find()) {
        return rule.race;
      }
    }
    return defaultRace;
  }

  String raceForCategories(List<String> categories) {
    if (categories == null || categories.isEmpty()) {
      return null;
    }
    String joined = String.join(" ", categories).toLowerCase(Locale.ROOT);
    for (CategoryRule rule : categoryRules) {
      if (joined.contains(rule.keyword)) {
        return rule.race;
      }
    }
    return null;
  }

  String ethnicityKey(String leagueRegion, String location, List<String> categories) {
    if (leagueRegion == null) {
      return null;
    }
    String region = leagueRegion.trim();
    if (region.contains(",") || region.contains("&")) {
      return null;
    }
    String key = region.toLowerCase(Locale.ROOT);
    if (key.equals(desertLeagueRegion)) {
      StringBuilder hint = new StringBuilder(location == null ? "" : location);
      if (categories != null) {
        for (String category : categories) {
          hint.append(' ').append(category);
        }
      }
      return menaphiteHint.matcher(hint).find() ? menaphiteEthnicity : desertEthnicity;
    }
    return leagueRegionEthnicity.get(key);
  }

  boolean isNpcPage(String wikitext) {
    return wikitext != null && infoboxTemplates.matcher(wikitext).find();
  }

  private static Pattern infoboxPattern(JsonArray templates) {
    StringBuilder alternatives = new StringBuilder();
    for (JsonElement element : templates) {
      String name = element.getAsString().replaceFirst("^[^:]+:", "");
      alternatives.append(alternatives.length() == 0 ? "" : "|");
      alternatives.append(Pattern.quote(name).replace(" ", "\\E[ _]+\\Q"));
    }
    return Pattern.compile("\\{\\{\\s*(" + alternatives + ")", Pattern.CASE_INSENSITIVE);
  }

  private static WikiMapping load() {
    try (InputStream stream = WikiMapping.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException("Missing bundled resource " + RESOURCE);
      }
      JsonObject root =
          new JsonParser()
              .parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
              .getAsJsonObject();
      return new WikiMapping(root);
    } catch (Exception e) {
      throw new IllegalStateException("Could not read " + RESOURCE, e);
    }
  }

  private static final class RacePattern {
    private final Pattern pattern;
    private final String race;

    private RacePattern(Pattern pattern, String race) {
      this.pattern = pattern;
      this.race = race;
    }
  }

  private static final class CategoryRule {
    private final String keyword;
    private final String race;

    private CategoryRule(String keyword, String race) {
      this.keyword = keyword;
      this.race = race;
    }
  }
}
