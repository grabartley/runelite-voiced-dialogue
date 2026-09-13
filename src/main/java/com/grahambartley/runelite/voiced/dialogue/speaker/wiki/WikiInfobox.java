package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WikiInfobox {

  private static final Pattern RACE = versionedField("race");
  private static final Pattern GENDER = versionedField("gender");
  private static final Pattern ID = versionedField("id");
  private static final Pattern LEAGUE_REGION = field("leagueRegion");
  private static final Pattern LOCATION = field("location");
  private static final Pattern ID_SEPARATOR = Pattern.compile("[,\\s]+");
  private static final Pattern REF_TAG =
      Pattern.compile("<ref[^>]*>.*?</ref>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
  private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
  private static final Pattern TEMPLATE = Pattern.compile("\\{\\{[^}]*\\}\\}");

  private static Pattern versionedField(String key) {
    return field(key + "\\d*");
  }

  private static Pattern field(String key) {
    return Pattern.compile("\\|\\s*" + key + "\\s*=\\s*([^\\n|]+)", Pattern.CASE_INSENSITIVE);
  }

  private final String race;
  private final String leagueRegion;
  private final String location;
  private final List<String> genders;
  private final List<List<Integer>> idGroups;
  private final List<String> categories;

  private WikiInfobox(
      String race,
      String leagueRegion,
      String location,
      List<String> genders,
      List<List<Integer>> idGroups,
      List<String> categories) {
    this.race = race;
    this.leagueRegion = leagueRegion;
    this.location = location;
    this.genders = genders;
    this.idGroups = idGroups;
    this.categories = categories;
  }

  static WikiInfobox parse(String wikitext, List<String> categories) {
    return new WikiInfobox(
        firstField(RACE, wikitext),
        firstField(LEAGUE_REGION, wikitext),
        firstField(LOCATION, wikitext),
        allFields(GENDER, wikitext),
        idGroups(wikitext),
        categories == null ? Collections.<String>emptyList() : categories);
  }

  String race() {
    return race;
  }

  String leagueRegion() {
    return leagueRegion;
  }

  String location() {
    return location;
  }

  List<String> categories() {
    return categories;
  }

  String genderForVersion(int npcId) {
    if (genders.isEmpty()) {
      return null;
    }
    if (genders.size() != idGroups.size()) {
      return genders.get(0);
    }
    for (int index = 0; index < idGroups.size(); index++) {
      if (idGroups.get(index).contains(npcId)) {
        return genders.get(index);
      }
    }
    return genders.get(0);
  }

  private static List<List<Integer>> idGroups(String wikitext) {
    List<List<Integer>> groups = new ArrayList<>();
    Matcher matcher = ID.matcher(wikitext);
    while (matcher.find()) {
      List<Integer> ids = new ArrayList<>();
      for (String token : ID_SEPARATOR.split(clean(matcher.group(1)))) {
        if (!token.isEmpty() && token.chars().allMatch(Character::isDigit)) {
          ids.add(Integer.valueOf(token));
        }
      }
      if (!ids.isEmpty()) {
        groups.add(ids);
      }
    }
    return groups;
  }

  private static List<String> allFields(Pattern pattern, String wikitext) {
    List<String> values = new ArrayList<>();
    Matcher matcher = pattern.matcher(wikitext);
    while (matcher.find()) {
      values.add(clean(matcher.group(1)));
    }
    return values;
  }

  private static String firstField(Pattern pattern, String wikitext) {
    Matcher matcher = pattern.matcher(wikitext);
    return matcher.find() ? clean(matcher.group(1)) : null;
  }

  private static String clean(String value) {
    String cleaned = REF_TAG.matcher(value).replaceAll("");
    cleaned = HTML_TAG.matcher(cleaned).replaceAll("");
    cleaned = cleaned.replace("[[", "").replace("]]", "");
    cleaned = TEMPLATE.matcher(cleaned).replaceAll("");
    return cleaned.trim();
  }
}
