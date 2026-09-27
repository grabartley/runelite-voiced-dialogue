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
final class GeminiVoiceRegions {

  static final String RESOURCE = "/voice-regions.json";

  static final String REGIONS_KEY = "regions";

  static final String NARRATOR_KEY = "narratorVoice";

  static final String PLAYER_KEYWORDS_KEY = "playerKeywords";

  private static final long HASH_MULTIPLIER = 0x9E3779B97F4A7C15L;

  private static final long UNSIGNED_INT_MASK = 0xFFFFFFFFL;

  private static final int SEED_SHIFT = 32;

  private static final int FIRST_MIX_SHIFT = 31;

  private static final int SECOND_MIX_SHIFT = 29;

  static final String CHILD_POOL_PREFIX = "CHILD_";

  private static final NpcGender[] VOICED_GENDERS = {NpcGender.MALE, NpcGender.FEMALE};

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
      Map<NpcGender, List<String>> childByGender = new EnumMap<>(NpcGender.class);
      for (NpcGender gender : VOICED_GENDERS) {
        byGender.put(gender, strings(region, gender.name()));
        childByGender.put(gender, strings(region, CHILD_POOL_PREFIX + gender.name()));
      }
      pools.put(entry.getKey(), byGender);
      childPools.put(entry.getKey(), childByGender);
      List<Pattern> keywords = new ArrayList<>();
      for (String keyword : strings(region, PLAYER_KEYWORDS_KEY)) {
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
    long mixed = (((long) seed) << SEED_SHIFT) ^ (voice.hashCode() & UNSIGNED_INT_MASK);
    mixed *= HASH_MULTIPLIER;
    mixed ^= mixed >>> FIRST_MIX_SHIFT;
    mixed *= HASH_MULTIPLIER;
    return mixed ^ (mixed >>> SECOND_MIX_SHIFT);
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
            root.has(REGIONS_KEY) && root.get(REGIONS_KEY).isJsonObject()
                ? root.getAsJsonObject(REGIONS_KEY)
                : new JsonObject();
        String narrator = root.has(NARRATOR_KEY) ? root.get(NARRATOR_KEY).getAsString() : null;
        return new GeminiVoiceRegions(regions, narrator);
      } catch (Exception e) {
        log.error("Failed to load voice region table {}: {}", RESOURCE, e.getMessage());
        return new GeminiVoiceRegions(new JsonObject());
      }
    }
  }
}
