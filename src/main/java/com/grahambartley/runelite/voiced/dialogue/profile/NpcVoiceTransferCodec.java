package com.grahambartley.runelite.voiced.dialogue.profile;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

public final class NpcVoiceTransferCodec {

  public static final String FORMAT = "voiced-dialogue-npc-voices";
  public static final int VERSION = 1;

  private final NpcVoiceOverrideStore store;
  private final Gson gson;

  public NpcVoiceTransferCodec(NpcVoiceOverrideStore store, Gson gson) {
    this.store = store;
    this.gson = gson.newBuilder().setPrettyPrinting().create();
  }

  public String encode(Map<Integer, NpcVoiceOverride> overrides) {
    JsonObject entries = new JsonObject();
    new TreeMap<>(overrides)
        .forEach(
            (id, override) -> entries.add(id.toString(), NpcVoiceOverrideJson.toJson(override)));
    JsonObject document = new JsonObject();
    document.addProperty("format", FORMAT);
    document.addProperty("version", VERSION);
    document.add("overrides", entries);
    return gson.toJson(document);
  }

  public NpcVoiceImport decode(String text) throws InvalidDocumentException {
    JsonObject document = parseDocument(text);
    if (!FORMAT.equals(stringOrNull(document.get("format")))) {
      throw new InvalidDocumentException("That is not a Voiced Dialogue NPC voices file.");
    }
    checkVersion(document.get("version"));
    JsonElement entries = document.get("overrides");
    if (entries == null || !entries.isJsonObject()) {
      throw new InvalidDocumentException("The file has no NPC voices list.");
    }
    Map<Integer, NpcVoiceOverride> overrides = new TreeMap<>();
    int skipped = 0;
    for (Map.Entry<String, JsonElement> entry : entries.getAsJsonObject().entrySet()) {
      Integer id = npcId(entry.getKey());
      NpcVoiceOverride override = id == null ? null : override(entry.getValue());
      if (override == null) {
        skipped++;
      } else {
        overrides.put(id, override);
      }
    }
    return new NpcVoiceImport(overrides, skipped);
  }

  private static JsonObject parseDocument(String text) throws InvalidDocumentException {
    JsonElement parsed;
    try {
      parsed = new JsonParser().parse(text == null ? "" : text);
    } catch (RuntimeException e) {
      throw new InvalidDocumentException("That is not valid JSON.");
    }
    if (!parsed.isJsonObject()) {
      throw new InvalidDocumentException("That is not a Voiced Dialogue NPC voices file.");
    }
    return parsed.getAsJsonObject();
  }

  private static void checkVersion(JsonElement version) throws InvalidDocumentException {
    boolean known =
        version != null
            && version.isJsonPrimitive()
            && version.getAsJsonPrimitive().isNumber()
            && version.getAsBigDecimal().compareTo(BigDecimal.valueOf(VERSION)) == 0;
    if (!known) {
      throw new InvalidDocumentException(
          "This file uses a version this plugin does not know. Update Voiced Dialogue and try"
              + " again.");
    }
  }

  private static Integer npcId(String key) {
    try {
      int id = Integer.parseInt(key);
      return id < 0 ? null : id;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private NpcVoiceOverride override(JsonElement value) {
    NpcVoiceOverride sanitized;
    try {
      sanitized = store.sanitize(NpcVoiceOverrideJson.parse(value));
    } catch (RuntimeException e) {
      return null;
    }
    return sanitized.isEmpty() ? null : sanitized;
  }

  private static String stringOrNull(JsonElement element) {
    return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
        ? element.getAsString()
        : null;
  }

  public static final class InvalidDocumentException extends Exception {
    InvalidDocumentException(String message) {
      super(message);
    }
  }
}
