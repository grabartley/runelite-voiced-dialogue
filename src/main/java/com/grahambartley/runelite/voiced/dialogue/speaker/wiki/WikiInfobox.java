package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WikiInfobox {

  private static final Pattern RACE = versionedField("race");
  private static final Pattern GENDER = versionedField("gender");
  private static final Pattern ID = versionedField("id");
  private static final Pattern LEAGUE_REGION = field("leagueRegion");
  private static final Pattern LOCATION = field("location");
  private static final Pattern ID_SEPARATOR = Pattern.compile("[,\\s]+");
  private static final Pattern LINK = Pattern.compile("\\[\\[([^\\[\\]]*)\\]\\]");
  private static final Pattern REF_TAG =
      Pattern.compile("<ref[^>]*>.*?</ref>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
  private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
  private static final Pattern TEMPLATE = Pattern.compile("\\{\\{[^}]*\\}\\}");
  private static final UnaryOperator<String> LINK_TARGET = body -> body.split("\\|", -1)[0];
  private static final UnaryOperator<String> LINK_DISPLAY =
      body -> {
        String[] parts = body.split("\\|", -1);
        return parts[parts.length - 1];
      };

  private static Pattern versionedField(String key) {
    return field(key + "\\d*");
  }

  private static Pattern field(String key) {
    return Pattern.compile("\\|\\s*" + key + "\\s*=\\s*([^\\n]+)", Pattern.CASE_INSENSITIVE);
  }

  private final List<String> raceReadings;
  private final String leagueRegion;
  private final String location;
  private final List<String> genders;
  private final List<List<Integer>> idGroups;
  private final List<String> categories;

  private WikiInfobox(
      List<String> raceReadings,
      String leagueRegion,
      String location,
      List<String> genders,
      List<List<Integer>> idGroups,
      List<String> categories) {
    this.raceReadings = raceReadings;
    this.leagueRegion = leagueRegion;
    this.location = location;
    this.genders = genders;
    this.idGroups = idGroups;
    this.categories = categories;
  }

  static WikiInfobox parse(String wikitext, List<String> categories) {
    return new WikiInfobox(
        linkReadings(rawField(RACE, wikitext)),
        firstField(LEAGUE_REGION, wikitext),
        firstField(LOCATION, wikitext),
        cleanFields(GENDER, wikitext),
        idGroups(wikitext),
        categories == null ? Collections.<String>emptyList() : categories);
  }

  List<String> raceReadings() {
    return raceReadings;
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
    for (String raw : fieldTexts(ID, wikitext)) {
      List<Integer> ids = new ArrayList<>();
      for (String token : ID_SEPARATOR.split(clean(raw, LINK_TARGET))) {
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

  private static List<String> cleanFields(Pattern pattern, String wikitext) {
    List<String> values = new ArrayList<>();
    for (String raw : fieldTexts(pattern, wikitext)) {
      values.add(clean(raw, LINK_TARGET));
    }
    return values;
  }

  private static String firstField(Pattern pattern, String wikitext) {
    String raw = rawField(pattern, wikitext);
    return raw == null ? null : clean(raw, LINK_TARGET);
  }

  private static String rawField(Pattern pattern, String wikitext) {
    List<String> texts = fieldTexts(pattern, wikitext);
    return texts.isEmpty() ? null : texts.get(0);
  }

  private static List<String> fieldTexts(Pattern pattern, String wikitext) {
    List<String> values = new ArrayList<>();
    Matcher matcher = pattern.matcher(wikitext);
    int from = 0;
    while (from <= wikitext.length() && matcher.find(from)) {
      String text = fieldText(matcher.group(1));
      values.add(text);
      from = matcher.start(1) + text.length();
    }
    return values;
  }

  private static String fieldText(String value) {
    int depth = 0;
    int index = 0;
    while (index < value.length()) {
      String token = value.length() - index >= 2 ? value.substring(index, index + 2) : "";
      if ("[[".equals(token) || "{{".equals(token)) {
        depth++;
        index += 2;
      } else if ("]]".equals(token) || "}}".equals(token)) {
        if (depth == 0) {
          return value.substring(0, index);
        }
        depth--;
        index += 2;
      } else if (value.charAt(index) == '|' && depth == 0) {
        return value.substring(0, index);
      } else {
        index++;
      }
    }
    return value;
  }

  private static List<String> linkReadings(String value) {
    List<String> readings = new ArrayList<>();
    if (value == null) {
      return readings;
    }
    String target = clean(value, LINK_TARGET);
    String display = clean(value, LINK_DISPLAY);
    if (!target.isEmpty()) {
      readings.add(target);
    }
    if (!display.isEmpty() && !display.equals(target)) {
      readings.add(display);
    }
    return readings;
  }

  private static String clean(String value, UnaryOperator<String> side) {
    String cleaned = REF_TAG.matcher(value).replaceAll("");
    cleaned = HTML_TAG.matcher(cleaned).replaceAll("");
    cleaned = resolveLinks(cleaned, side);
    cleaned = cleaned.replace("[[", "").replace("]]", "");
    cleaned = TEMPLATE.matcher(cleaned).replaceAll("");
    return cleaned.trim();
  }

  private static String resolveLinks(String value, UnaryOperator<String> side) {
    Matcher matcher = LINK.matcher(value);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(out, Matcher.quoteReplacement(side.apply(matcher.group(1))));
    }
    matcher.appendTail(out);
    return out.toString();
  }
}
