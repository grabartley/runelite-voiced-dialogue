package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class AiStudioErrorDetails {

  private AiStudioErrorDetails() {}

  static List<JsonObject> ofType(Gson gson, byte[] body, String type) {
    if (body == null || body.length == 0) {
      return Collections.emptyList();
    }
    try {
      JsonObject document =
          gson.fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
      JsonObject error = document == null ? null : document.getAsJsonObject("error");
      JsonArray details = error == null ? null : error.getAsJsonArray("details");
      if (details == null) {
        return Collections.emptyList();
      }
      List<JsonObject> matches = new ArrayList<>();
      for (JsonElement element : details) {
        if (!element.isJsonObject()) {
          continue;
        }
        JsonObject detail = element.getAsJsonObject();
        if (type.equals(text(detail, "@type"))) {
          matches.add(detail);
        }
      }
      return matches;
    } catch (RuntimeException e) {
      log.debug("[TTS cloud] AI Studio quota failure parse error: {}", e.getMessage());
      return Collections.emptyList();
    }
  }

  static JsonArray array(JsonObject object, String field) {
    if (object == null) {
      return null;
    }
    JsonElement value = object.get(field);
    return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
  }

  static String text(JsonObject object, String field) {
    if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
      return "";
    }
    try {
      return object.get(field).getAsString();
    } catch (RuntimeException e) {
      return "";
    }
  }
}
