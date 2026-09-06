package com.grahambartley.runelite.voiced.dialogue.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcProfileLayers.CategoryRule;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcProfileLayers.Layer;
import com.grahambartley.runelite.voiced.dialogue.speaker.LifeStage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the {@code profiles} section of the bundled resource into {@link NpcProfileLayers}, so
 * loading and parsing stay separate from resolution. Every failure is survivable: an unreadable
 * resource or a malformed entry is reported and skipped, leaving the built-in default in charge.
 */
@Slf4j
final class NpcProfileParser {

  private NpcProfileParser() {}

  /** Parses the {@code profiles} section of a bundled resource, or {@code null} when unusable. */
  static NpcProfileLayers loadResource(String resource) {
    try (InputStream stream = NpcProfileParser.class.getResourceAsStream(resource)) {
      if (stream == null) {
        log.warn(
            "NPC profile table {} not found - using the built-in British default for every line",
            resource);
        return null;
      }
      // Note: the bundled Gson predates the static JsonParser.parseReader API, so the instance
      // method is used here.
      JsonObject root =
          new JsonParser()
              .parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
              .getAsJsonObject();
      if (!root.has("profiles") || !root.get("profiles").isJsonObject()) {
        log.warn(
            "NPC profile table {} has no 'profiles' object - using the built-in British default",
            resource);
        return null;
      }
      return parse(root.getAsJsonObject("profiles"));
    } catch (Exception e) {
      log.error("Failed to load NPC profile table {}: {}", resource, e.getMessage());
      return null;
    }
  }

  static NpcProfileLayers parse(JsonObject profiles) {
    CharacterProfile parsedDefault = parseComplete(optObject(profiles, "default"));
    if (parsedDefault == null) {
      log.warn("profiles.default is missing or incomplete - using the built-in British default");
      parsedDefault = NpcProfileLayers.BUILTIN_DEFAULT;
    }
    return new NpcProfileLayers(
        parsedDefault,
        parseLayer(optObject(profiles, "player")),
        parseLayerMap(optObject(profiles, "byRace")),
        parseLayerMap(optObject(profiles, "byEthnicity")),
        parseCategories(profiles),
        parseByIdLayers(profiles));
  }

  private static List<CategoryRule> parseCategories(JsonObject profiles) {
    List<CategoryRule> categories = new ArrayList<>();
    if (profiles.has("byCategory") && profiles.get("byCategory").isJsonArray()) {
      JsonArray arr = profiles.getAsJsonArray("byCategory");
      for (JsonElement el : arr) {
        if (!el.isJsonObject()) {
          continue;
        }
        JsonObject entry = el.getAsJsonObject();
        if (!entry.has("keywords") || !entry.get("keywords").isJsonArray()) {
          continue;
        }
        List<String> keywords = new ArrayList<>();
        for (JsonElement kw : entry.getAsJsonArray("keywords")) {
          keywords.add(kw.getAsString().toLowerCase(Locale.ROOT));
        }
        String id = entry.has("id") ? entry.get("id").getAsString() : "category";
        boolean child = LifeStage.CHILD.equalsIgnoreCase(optString(entry, "lifeStage"));
        categories.add(new CategoryRule(id, keywords, parseLayer(entry), child));
      }
    }
    return Collections.unmodifiableList(categories);
  }

  private static Map<Integer, Layer> parseByIdLayers(JsonObject profiles) {
    Map<Integer, Layer> ids = new HashMap<>();
    JsonObject idObj = optObject(profiles, "byId");
    if (idObj != null) {
      for (String key : idObj.keySet()) {
        if (isComment(key) || !idObj.get(key).isJsonObject()) {
          continue;
        }
        try {
          ids.put(Integer.parseInt(key), parseLayer(idObj.getAsJsonObject(key)));
        } catch (NumberFormatException e) {
          log.warn("Skipping non-numeric byId profile key '{}'", key);
        }
      }
    }
    return Collections.unmodifiableMap(ids);
  }

  /**
   * Parses an object of {@code key -> sparse layer} (e.g. byRace, byEthnicity), keyed lower-case.
   */
  private static Map<String, Layer> parseLayerMap(JsonObject obj) {
    if (obj == null) {
      return Collections.emptyMap();
    }
    Map<String, Layer> map = new HashMap<>();
    for (String key : obj.keySet()) {
      if (isComment(key) || !obj.get(key).isJsonObject()) {
        continue;
      }
      map.put(key.toLowerCase(Locale.ROOT), parseLayer(obj.getAsJsonObject(key)));
    }
    return Collections.unmodifiableMap(map);
  }

  /** Parses a sparse layer; absent fields stay {@code null} so they inherit. */
  private static Layer parseLayer(JsonObject obj) {
    if (obj == null) {
      return null;
    }
    return new Layer(
        optString(obj, "name"),
        optString(obj, "accent"),
        optString(obj, "style"),
        optString(obj, "pace"));
  }

  /**
   * Parses a layer that must be complete (all four fields); returns {@code null} if any is absent.
   */
  private static CharacterProfile parseComplete(JsonObject obj) {
    Layer layer = parseLayer(obj);
    if (layer == null
        || layer.name() == null
        || layer.accent() == null
        || layer.style() == null
        || layer.pace() == null) {
      return null;
    }
    return new CharacterProfile(layer.name(), layer.accent(), layer.style(), layer.pace());
  }

  private static JsonObject optObject(JsonObject parent, String key) {
    return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
  }

  private static String optString(JsonObject obj, String key) {
    if (!obj.has(key) || obj.get(key).isJsonNull()) {
      return null;
    }
    String value = obj.get(key).getAsString();
    return value.isEmpty() ? null : value;
  }

  private static boolean isComment(String key) {
    return key.startsWith("_");
  }
}
