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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class GeminiVoiceRegions {

  static final String RESOURCE = "/voice-regions.json";

  static final String REGIONS_KEY = "regions";

  static final String NARRATOR_KEY = "narratorVoice";

  static final String PLAYER_KEYWORDS_KEY = "playerKeywords";

  static final String VOICE_AGES_KEY = "voiceAges";

  static final int AGE_WINDOW = 10;

  static final int MIN_AGE_MATCHES = 3;

  private static final long HASH_MULTIPLIER = 0x9E3779B97F4A7C15L;

  private static final long UNSIGNED_INT_MASK = 0xFFFFFFFFL;

  private static final int SEED_SHIFT = 32;

  private static final int FIRST_MIX_SHIFT = 31;

  private static final int SECOND_MIX_SHIFT = 29;

  static final String CHILD_POOL_PREFIX = "CHILD_";

  static final String PLAYER_VOICE_PREFIX = "PLAYER_";

  private static final NpcGender[] VOICED_GENDERS = {NpcGender.MALE, NpcGender.FEMALE};

  private final Map<String, Map<NpcGender, List<String>>> pools = new LinkedHashMap<>();
  private final Map<String, Map<NpcGender, List<String>>> childPools = new LinkedHashMap<>();
  private final Map<String, Map<NpcGender, String>> playerVoices = new LinkedHashMap<>();
  private final Map<String, List<Pattern>> playerKeywords = new LinkedHashMap<>();
  private final Map<String, Integer> voiceAges;
  private final String narratorVoice;

  GeminiVoiceRegions(JsonObject regions) {
    this(regions, null);
  }

  GeminiVoiceRegions(JsonObject regions, String narratorVoice) {
    this(regions, narratorVoice, Collections.emptyMap());
  }

  GeminiVoiceRegions(JsonObject regions, String narratorVoice, Map<String, Integer> voiceAges) {
    this.narratorVoice = narratorVoice;
    this.voiceAges = voiceAges;
    for (Map.Entry<String, JsonElement> entry : regions.entrySet()) {
      if (!entry.getValue().isJsonObject()) {
        continue;
      }
      JsonObject region = entry.getValue().getAsJsonObject();
      Map<NpcGender, List<String>> byGender = new EnumMap<>(NpcGender.class);
      Map<NpcGender, List<String>> childByGender = new EnumMap<>(NpcGender.class);
      Map<NpcGender, String> playerByGender = new EnumMap<>(NpcGender.class);
      for (NpcGender gender : VOICED_GENDERS) {
        byGender.put(gender, strings(region, gender.name()));
        childByGender.put(gender, strings(region, CHILD_POOL_PREFIX + gender.name()));
        String playerKey = PLAYER_VOICE_PREFIX + gender.name();
        if (region.has(playerKey)) {
          playerByGender.put(gender, region.get(playerKey).getAsString());
        }
      }
      pools.put(entry.getKey(), byGender);
      childPools.put(entry.getKey(), childByGender);
      playerVoices.put(entry.getKey(), playerByGender);
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
    return voiceFor(region, gender, seed, null);
  }

  String voiceFor(String region, NpcGender gender, int seed, Integer age) {
    List<String> pool = pool(pools, region, gender);
    return pick(age == null ? pool : closestInAge(pool, age), seed);
  }

  private List<String> closestInAge(List<String> pool, int age) {
    if (pool == null) {
      return null;
    }
    List<String> aged =
        pool.stream()
            .filter(voiceAges::containsKey)
            .sorted(
                Comparator.<String>comparingInt(voice -> ageGap(voice, age))
                    .thenComparing(Comparator.naturalOrder()))
            .collect(Collectors.toList());
    if (aged.isEmpty()) {
      return pool;
    }
    List<String> matches = new ArrayList<>();
    for (String voice : aged) {
      if (matches.size() >= MIN_AGE_MATCHES && ageGap(voice, age) > AGE_WINDOW) {
        break;
      }
      matches.add(voice);
    }
    return matches;
  }

  private int ageGap(String voice, int age) {
    return Math.abs(voiceAges.get(voice) - age);
  }

  String voiceExcluding(String region, NpcGender gender, int seed, String excluded) {
    List<String> pool = pool(pools, region, gender);
    if (pool == null || !pool.contains(excluded)) {
      return pick(pool, seed);
    }
    List<String> remaining = new ArrayList<>(pool);
    remaining.remove(excluded);
    return pick(remaining, seed);
  }

  String playerVoiceFor(String region, NpcGender gender, int seed) {
    Map<NpcGender, String> pinned = region == null ? null : playerVoices.get(region);
    String voice = pinned == null ? null : pinned.get(gender);
    return voice != null ? voice : voiceFor(region, gender, seed);
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

  static Map<String, Integer> ages(JsonObject root) {
    if (!root.has(VOICE_AGES_KEY) || !root.get(VOICE_AGES_KEY).isJsonObject()) {
      return Collections.emptyMap();
    }
    Map<String, Integer> ages = new HashMap<>();
    for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(VOICE_AGES_KEY).entrySet()) {
      JsonElement value = entry.getValue();
      if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
        ages.put(entry.getKey(), value.getAsInt());
      }
    }
    return Collections.unmodifiableMap(ages);
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
        return new GeminiVoiceRegions(regions, narrator, ages(root));
      } catch (Exception e) {
        log.error("Failed to load voice region table {}: {}", RESOURCE, e.getMessage());
        return new GeminiVoiceRegions(new JsonObject());
      }
    }
  }
}
