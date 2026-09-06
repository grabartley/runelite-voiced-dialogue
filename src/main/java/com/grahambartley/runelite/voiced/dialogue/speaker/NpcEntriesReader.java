package com.grahambartley.runelite.voiced.dialogue.speaker;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Reads the {@code {"npcs": {id: {race, gender, ethnicity?, lifeStage?}}}} document shared by the
 * bundled voice table and the runtime learned store, so the two can never drift apart. A malformed
 * entry is skipped and reported rather than failing the whole read, since one bad row must not cost
 * every other NPC its voice.
 */
final class NpcEntriesReader {

  private NpcEntriesReader() {}

  /**
   * Parses every well-formed entry, tagging each with {@code source}. Returns {@code null} when the
   * document carries no {@code npcs} object, so the caller can report that in its own terms. Throws
   * whatever the JSON layer throws on an unreadable document.
   */
  static Map<Integer, NpcAttributes> read(
      Reader reader, String source, BiConsumer<String, RuntimeException> onSkippedEntry) {
    // Note: the bundled Gson predates the static JsonParser.parseReader API, so the instance
    // method is used here.
    JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
    if (!root.has("npcs") || !root.get("npcs").isJsonObject()) {
      return null;
    }

    JsonObject npcs = root.getAsJsonObject("npcs");
    Map<Integer, NpcAttributes> entries = new HashMap<>();
    for (String key : npcs.keySet()) {
      try {
        int npcId = Integer.parseInt(key);
        JsonObject entry = npcs.getAsJsonObject(key);
        NpcAttributes attributes =
            new NpcAttributes(
                entry.get("race").getAsString(), entry.get("gender").getAsString(), source);
        attributes.setNpcId(npcId);
        attributes.setEthnicity(optString(entry, "ethnicity"));
        attributes.setLifeStage(optString(entry, "lifeStage"));
        entries.put(npcId, attributes);
      } catch (RuntimeException e) {
        onSkippedEntry.accept(key, e);
      }
    }
    return entries;
  }

  private static String optString(JsonObject entry, String key) {
    return entry.has(key) && !entry.get(key).isJsonNull() ? entry.get(key).getAsString() : null;
  }
}
