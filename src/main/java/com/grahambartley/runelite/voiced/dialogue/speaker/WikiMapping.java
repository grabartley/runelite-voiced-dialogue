package com.grahambartley.runelite.voiced.dialogue.speaker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class WikiMapping {

  private static final String RESOURCE = "/wiki-mapping.json";

  private static final WikiMapping INSTANCE = load();

  private final List<RacePattern> raceRules;
  private final List<CategoryRule> categoryRules;
  private final Map<String, String> leagueRegionEthnicity;
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
    defaultRace = root.get("defaultRace").getAsString();
    defaultGender = root.get("defaultGender").getAsString();
    femaleGender = root.get("femaleGender").getAsString();
    JsonObject desert = root.getAsJsonObject("desert");
    desertLeagueRegion = desert.get("leagueRegion").getAsString().toLowerCase(Locale.ROOT);
    menaphiteHint = Pattern.compile(desert.get("hint").getAsString(), Pattern.CASE_INSENSITIVE);
    menaphiteEthnicity = desert.get("hinted").getAsString();
    desertEthnicity = desert.get("default").getAsString();
  }

  public static WikiMapping get() {
    return INSTANCE;
  }

  public String defaultRace() {
    return defaultRace;
  }

  public String defaultGender() {
    return defaultGender;
  }

  public String genderForWikiText(String genderText) {
    if (genderText != null) {
      String normalised = genderText.trim().toLowerCase(Locale.ROOT);
      if (normalised.startsWith("f")) {
        return femaleGender;
      }
    }
    return defaultGender;
  }

  public String raceForWikiText(String raceText) {
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

  public String raceForCategories(List<String> categories) {
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

  public String ethnicityKey(String leagueRegion, String location, List<String> categories) {
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

  List<String> races() {
    List<String> races = new ArrayList<>();
    for (RacePattern rule : raceRules) {
      races.add(rule.race);
    }
    for (CategoryRule rule : categoryRules) {
      races.add(rule.race);
    }
    return Collections.unmodifiableList(races);
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
