package com.grahambartley.runelite.voiced.dialogue.speech;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class PronunciationGuide {

  static final String RESOURCE = "/pronunciations.json";

  static final PronunciationGuide EMPTY = new PronunciationGuide(Collections.emptyMap());

  private static final Pattern CURLY_APOSTROPHE = Pattern.compile("’");

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private static final String APOSTROPHE_EITHER_WAY = "['’]";

  private static final String NOT_AFTER_WORD = "(?<![\\p{L}\\p{N}])";

  private static final String NOT_BEFORE_WORD = "(?![\\p{L}\\p{N}])";

  private final Map<String, Entry> entries;

  private final Pattern names;

  private PronunciationGuide(Map<String, Entry> entries) {
    this.entries = entries;
    this.names = entries.isEmpty() ? null : namePattern(entries.values());
  }

  static PronunciationGuide load() {
    try (InputStream stream = PronunciationGuide.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        log.warn("Pronunciation table {} not found - names are spoken as written", RESOURCE);
        return EMPTY;
      }
      JsonObject root =
          new JsonParser()
              .parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
              .getAsJsonObject();
      return parse(root);
    } catch (Exception e) {
      log.warn(
          "Failed to load pronunciation table {} - names are spoken as written: {}",
          RESOURCE,
          e.getMessage());
      return EMPTY;
    }
  }

  static PronunciationGuide parse(JsonObject root) {
    Map<String, Entry> parsed = new LinkedHashMap<>();
    for (JsonElement element : root.getAsJsonArray("words")) {
      JsonObject row = element.getAsJsonObject();
      String word = required(row, "word");
      String say = required(row, "say");
      String key = key(word);
      if (parsed.containsKey(key)) {
        throw new IllegalArgumentException("pronunciations.json lists " + word + " twice");
      }
      parsed.put(key, new Entry(word, say));
    }
    return new PronunciationGuide(Collections.unmodifiableMap(parsed));
  }

  int size() {
    return entries.size();
  }

  String respell(String text) {
    if (names == null || text == null) {
      return text;
    }
    Matcher matcher = names.matcher(text);
    StringBuilder spoken = new StringBuilder();
    while (matcher.find()) {
      Entry entry = entries.get(key(matcher.group()));
      String said = entry == null ? matcher.group() : entry.respelling(matcher.group());
      matcher.appendReplacement(spoken, Matcher.quoteReplacement(said));
    }
    matcher.appendTail(spoken);
    return spoken.toString();
  }

  private static Pattern namePattern(Iterable<Entry> all) {
    List<Entry> longestFirst = new ArrayList<>();
    all.forEach(longestFirst::add);
    longestFirst.sort(Comparator.comparingInt((Entry e) -> e.word.length()).reversed());
    List<String> alternatives = new ArrayList<>();
    for (Entry entry : longestFirst) {
      alternatives.add(wordRegex(entry.word));
    }
    return Pattern.compile(
        NOT_AFTER_WORD + "(?:" + String.join("|", alternatives) + ")" + NOT_BEFORE_WORD,
        Pattern.CASE_INSENSITIVE);
  }

  private static String wordRegex(String word) {
    List<String> tokens = new ArrayList<>();
    for (String token : WHITESPACE.split(key(word))) {
      List<String> pieces = new ArrayList<>();
      for (String piece : token.split("'", -1)) {
        pieces.add(piece.isEmpty() ? "" : Pattern.quote(piece));
      }
      tokens.add(String.join(APOSTROPHE_EITHER_WAY, pieces));
    }
    return String.join("\\s+", tokens);
  }

  private static String key(String word) {
    String straight = CURLY_APOSTROPHE.matcher(word.trim()).replaceAll("'");
    return WHITESPACE.matcher(straight).replaceAll(" ").toLowerCase(Locale.ROOT);
  }

  private static String required(JsonObject row, String field) {
    JsonElement value = row.get(field);
    if (value == null || value.isJsonNull() || value.getAsString().trim().isEmpty()) {
      throw new IllegalArgumentException("pronunciations.json entry is missing " + field);
    }
    return value.getAsString().trim();
  }

  private static final class Entry {
    final String word;
    final String lowerSay;

    Entry(String word, String say) {
      this.word = word;
      this.lowerSay = say.toLowerCase(Locale.ROOT);
    }

    String respelling(String written) {
      if (!Character.isUpperCase(written.charAt(0))) {
        return lowerSay;
      }
      return Character.toUpperCase(lowerSay.charAt(0)) + lowerSay.substring(1);
    }
  }
}
