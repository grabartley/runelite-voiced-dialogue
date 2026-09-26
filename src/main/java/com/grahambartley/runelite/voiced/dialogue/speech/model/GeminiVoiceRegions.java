package com.grahambartley.runelite.voiced.dialogue.speech.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class GeminiVoiceRegions {

  static final String RESOURCE = "/voice-regions.json";

  private static final long HASH_MULTIPLIER = 0x9E3779B97F4A7C15L;

  static final String CHILD_POOL_PREFIX = "CHILD_";

  private final Map<String, Map<NpcGender, List<String>>> pools = new LinkedHashMap<>();
  private final Map<String, Map<NpcGender, List<String>>> childPools = new LinkedHashMap<>();
  private final Map<String, List<Pattern>> playerKeywords = new LinkedHashMap<>();
  private final String narratorVoice;

  GeminiVoiceRegions(JsonObject regions) {
    this(regions, null);
  }

  GeminiVoiceRegions(JsonObject regions, String narratorVoice) {
    this.narratorVoice = narratorVoice;
    for (Map.Entry<String, JsonElement> entry : regions.entrySet()) {
      if (!entry.getValue().isJsonObject()) {
        continue;
      }
      JsonObject region = entry.getValue().getAsJsonObject();
      Map<NpcGender, List<String>> byGender = new EnumMap<>(NpcGender.class);
      byGender.put(NpcGender.MALE, strings(region, "MALE"));
      byGender.put(NpcGender.FEMALE, strings(region, "FEMALE"));
      pools.put(entry.getKey(), byGender);
      Map<NpcGender, List<String>> childByGender = new EnumMap<>(NpcGender.class);
      childByGender.put(NpcGender.MALE, strings(region, CHILD_POOL_PREFIX + "MALE"));
      childByGender.put(NpcGender.FEMALE, strings(region, CHILD_POOL_PREFIX + "FEMALE"));
      childPools.put(entry.getKey(), childByGender);
      List<Pattern> keywords = new ArrayList<>();
      for (String keyword : strings(region, "playerKeywords")) {
        keywords.add(
            Pattern.compile("\\b" + Pattern.quote(keyword.toLowerCase(Locale.ROOT)) + "\\b"));
      }
      playerKeywords.put(entry.getKey(), keywords);
    }
  }

  static GeminiVoiceRegions bundled() {
    return Bundled.INSTANCE;
  }

  String narratorVoice() {
    return narratorVoice;
  }

  String voiceFor(String region, NpcGender gender, int seed) {
    return pick(pool(pools, region, gender), seed);
  }

  String childVoiceFor(String region, NpcGender gender, int seed) {
    return pick(pool(childPools, region, gender), seed);
  }

  private static List<String> pool(
      Map<String, Map<NpcGender, List<String>>> source, String region, NpcGender gender) {
    Map<NpcGender, List<String>> byGender = region == null ? null : source.get(region);
    return byGender == null ? null : byGender.get(gender);
  }

  static String pick(List<String> pool, int seed) {
    if (pool == null || pool.isEmpty()) {
      return null;
    }
    String chosen = null;
    long best = Long.MIN_VALUE;
    for (String voice : pool) {
      long weight = weight(seed, voice);
      if (chosen == null || weight > best) {
        best = weight;
        chosen = voice;
      }
    }
    return chosen;
  }

  String regionForAccent(String accent) {
    if (accent == null) {
      return null;
    }
    String lower = accent.toLowerCase(Locale.ROOT);
    for (Map.Entry<String, List<Pattern>> entry : playerKeywords.entrySet()) {
      for (Pattern keyword : entry.getValue()) {
        if (keyword.matcher(lower).find()) {
          return entry.getKey();
        }
      }
    }
    return null;
  }

  private static long weight(int seed, String voice) {
    long mixed = (((long) seed) << 32) ^ (voice.hashCode() & 0xFFFFFFFFL);
    mixed *= HASH_MULTIPLIER;
    mixed ^= mixed >>> 31;
    mixed *= HASH_MULTIPLIER;
    return mixed ^ (mixed >>> 29);
  }

  private static List<String> strings(JsonObject object, String key) {
    if (!object.has(key) || !object.get(key).isJsonArray()) {
      return Collections.emptyList();
    }
    List<String> values = new ArrayList<>();
    JsonArray array = object.getAsJsonArray(key);
    for (JsonElement element : array) {
      values.add(element.getAsString());
    }
    return Collections.unmodifiableList(values);
  }

  private static final class Bundled {
    static final GeminiVoiceRegions INSTANCE = load();

    private static GeminiVoiceRegions load() {
      try (InputStream stream = GeminiVoiceRegions.class.getResourceAsStream(RESOURCE)) {
        if (stream == null) {
          log.warn("Voice region table {} not found - every NPC keeps its race voice", RESOURCE);
          return new GeminiVoiceRegions(new JsonObject());
        }
        JsonObject root =
            new JsonParser()
                .parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonObject regions =
            root.has("regions") && root.get("regions").isJsonObject()
                ? root.getAsJsonObject("regions")
                : new JsonObject();
        String narrator =
            root.has("narratorVoice") ? root.get("narratorVoice").getAsString() : null;
        return new GeminiVoiceRegions(regions, narrator);
      } catch (Exception e) {
        log.error("Failed to load voice region table {}: {}", RESOURCE, e.getMessage());
        return new GeminiVoiceRegions(new JsonObject());
      }
    }
  }
}
