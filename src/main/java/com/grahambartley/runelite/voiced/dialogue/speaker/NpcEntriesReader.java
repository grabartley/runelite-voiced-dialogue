package com.grahambartley.runelite.voiced.dialogue.speaker;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

final class NpcEntriesReader {

  private NpcEntriesReader() {}

  static Map<Integer, NpcAttributes> read(
      Reader reader, String source, BiConsumer<String, RuntimeException> onSkippedEntry) {
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
