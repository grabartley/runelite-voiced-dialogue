package com.grahambartley.runelite.voiced.dialogue.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class NpcVoiceOverrideJson {

  private NpcVoiceOverrideJson() {}

  static NpcVoiceOverride parse(JsonElement element) {
    JsonObject json = element.getAsJsonObject();
    return new NpcVoiceOverride(
        optString(json, "name"),
        optString(json, "accent"),
        optString(json, "style"),
        optString(json, "pace"),
        parseVoiceType(optString(json, "voiceType")));
  }

  static JsonObject toJson(NpcVoiceOverride override) {
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

  private static VoiceType parseVoiceType(String voiceType) {
    return voiceType == null ? null : VoiceType.valueOf(voiceType);
  }

  private static String optString(JsonObject json, String field) {
    JsonElement value = json.get(field);
    return value == null || value.isJsonNull() ? null : value.getAsString();
  }

  private static void addIfSet(JsonObject json, String field, String value) {
    if (value != null) {
      json.addProperty(field, value);
    }
  }
}
