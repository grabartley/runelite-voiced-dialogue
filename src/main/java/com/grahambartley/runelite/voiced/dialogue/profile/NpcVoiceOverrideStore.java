package com.grahambartley.runelite.voiced.dialogue.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import java.util.Map;
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
  private final Map<Integer, NpcVoiceOverride> overrides = new ConcurrentHashMap<>();

  public NpcVoiceOverrideStore(ConfigManager configManager) {
    this(configManager, new DirectionSanitizer(new ProfanityFilter()));
  }

  NpcVoiceOverrideStore(ConfigManager configManager, DirectionSanitizer directionSanitizer) {
    this.configManager = configManager;
    this.directionSanitizer = directionSanitizer;
  }

  public synchronized void load() {
    overrides.clear();
    for (String wholeKey : configManager.getConfigurationKeys(WHOLE_KEY_PREFIX)) {
      String idPart = wholeKey.substring(WHOLE_KEY_PREFIX.length());
      String key = KEY_PREFIX + idPart;
      try {
        int npcId = Integer.parseInt(idPart);
        NpcVoiceOverride override =
            sanitize(parse(configManager.getConfiguration(VoicedDialogueConfig.GROUP, key)));
        if (!override.isEmpty()) {
          overrides.put(npcId, override);
        }
      } catch (RuntimeException e) {
        log.warn("Skipping malformed NPC voice override {}: {}", key, e.getMessage());
      }
    }
    log.info("Loaded {} NPC voice overrides", overrides.size());
  }

  public NpcVoiceOverride get(Integer npcId) {
    return npcId == null ? null : overrides.get(npcId);
  }

  public synchronized void set(int npcId, NpcVoiceOverride override) {
    NpcVoiceOverride sanitized = sanitize(override);
    if (sanitized.isEmpty()) {
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
        override.gender() == NpcGender.UNKNOWN ? null : override.gender());
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
        parseGender(optString(json, "gender")));
  }

  private static NpcGender parseGender(String gender) {
    if (gender == null) {
      return null;
    }
    if (gender.equalsIgnoreCase("Male")) {
      return NpcGender.MALE;
    }
    if (gender.equalsIgnoreCase("Female")) {
      return NpcGender.FEMALE;
    }
    throw new IllegalArgumentException("unknown gender '" + gender + "'");
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
    if (override.gender() == NpcGender.MALE) {
      json.addProperty("gender", "Male");
    } else if (override.gender() == NpcGender.FEMALE) {
      json.addProperty("gender", "Female");
    }
    return json;
  }

  private static void addIfSet(JsonObject json, String field, String value) {
    if (value != null) {
      json.addProperty(field, value);
    }
  }
}
