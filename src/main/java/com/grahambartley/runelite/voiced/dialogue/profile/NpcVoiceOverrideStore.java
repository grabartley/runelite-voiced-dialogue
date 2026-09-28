package com.grahambartley.runelite.voiced.dialogue.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
public final class NpcVoiceOverrideStore {

  static final String KEY_PREFIX = "npcVoice_";

  private static final String WHOLE_KEY_PREFIX =
      ConfigManager.getWholeKey(VoicedDialogueConfig.GROUP, null, KEY_PREFIX);

  private final ConfigManager configManager;
  private final DirectionSanitizer directionSanitizer;
  private volatile Map<Integer, NpcVoiceOverride> overrides = new ConcurrentHashMap<>();

  public NpcVoiceOverrideStore(ConfigManager configManager) {
    this(configManager, new DirectionSanitizer(new ProfanityFilter()));
  }

  NpcVoiceOverrideStore(ConfigManager configManager, DirectionSanitizer directionSanitizer) {
    this.configManager = configManager;
    this.directionSanitizer = directionSanitizer;
  }

  public synchronized void load() {
    Map<Integer, NpcVoiceOverride> loaded = new ConcurrentHashMap<>();
    for (String wholeKey : configManager.getConfigurationKeys(WHOLE_KEY_PREFIX)) {
      readInto(loaded, KEY_PREFIX + wholeKey.substring(WHOLE_KEY_PREFIX.length()));
    }
    overrides = loaded;
    log.info("Loaded {} NPC voice overrides", loaded.size());
  }

  public static boolean isOverrideKey(String key) {
    return key != null && key.startsWith(KEY_PREFIX);
  }

  public synchronized void refresh(String key) {
    if (isOverrideKey(key)) {
      readInto(overrides, key);
    }
  }

  private void readInto(Map<Integer, NpcVoiceOverride> target, String key) {
    int npcId;
    try {
      npcId = Integer.parseInt(key.substring(KEY_PREFIX.length()));
    } catch (NumberFormatException e) {
      log.warn("Skipping NPC voice override with a non-numeric id: {}", key);
      return;
    }
    NpcVoiceOverride override = read(key);
    if (override == null || override.isEmpty()) {
      target.remove(npcId);
    } else {
      target.put(npcId, override);
    }
  }

  private NpcVoiceOverride read(String key) {
    String value = configManager.getConfiguration(VoicedDialogueConfig.GROUP, key);
    if (value == null) {
      return null;
    }
    try {
      return sanitize(parse(value));
    } catch (RuntimeException e) {
      log.warn("Skipping malformed NPC voice override {}: {}", key, e.getMessage());
      return null;
    }
  }

  public NpcVoiceOverride get(Integer npcId) {
    return npcId == null ? null : overrides.get(npcId);
  }

  public Set<Integer> overriddenIds() {
    return new HashSet<>(overrides.keySet());
  }

  public synchronized void set(int npcId, NpcVoiceOverride override) {
    NpcVoiceOverride sanitized = override == null ? null : sanitize(override);
    if (sanitized == null || sanitized.isEmpty()) {
      clear(npcId);
      return;
    }
    overrides.put(npcId, sanitized);
    configManager.setConfiguration(
        VoicedDialogueConfig.GROUP, KEY_PREFIX + npcId, toJson(sanitized).toString());
  }

  public synchronized void clear(int npcId) {
    overrides.remove(npcId);
    configManager.unsetConfiguration(VoicedDialogueConfig.GROUP, KEY_PREFIX + npcId);
  }

  private NpcVoiceOverride sanitize(NpcVoiceOverride override) {
    return new NpcVoiceOverride(
        sanitize(override.name()),
        sanitize(override.accent()),
        sanitize(override.style()),
        sanitize(override.pace()),
        override.voiceType());
  }

  private String sanitize(String field) {
    String sanitized = directionSanitizer.sanitize(field);
    return sanitized == null || sanitized.isEmpty() ? null : sanitized;
  }

  private static NpcVoiceOverride parse(String value) {
    JsonObject json = new JsonParser().parse(value).getAsJsonObject();
    return new NpcVoiceOverride(
        optString(json, "name"),
        optString(json, "accent"),
        optString(json, "style"),
        optString(json, "pace"),
        parseVoiceType(optString(json, "voiceType")));
  }

  private static VoiceType parseVoiceType(String voiceType) {
    return voiceType == null ? null : VoiceType.valueOf(voiceType);
  }

  private static String optString(JsonObject json, String field) {
    JsonElement value = json.get(field);
    return value == null || value.isJsonNull() ? null : value.getAsString();
  }

  private static JsonObject toJson(NpcVoiceOverride override) {
    JsonObject json = new JsonObject();
    addIfSet(json, "name", override.name());
    addIfSet(json, "accent", override.accent());
    addIfSet(json, "style", override.style());
    addIfSet(json, "pace", override.pace());
    if (override.voiceType() != null) {
      json.addProperty("voiceType", override.voiceType().name());
    }
    return json;
  }

  private static void addIfSet(JsonObject json, String field, String value) {
    if (value != null) {
      json.addProperty(field, value);
    }
  }
}
