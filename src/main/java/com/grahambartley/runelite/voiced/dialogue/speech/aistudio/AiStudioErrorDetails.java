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

/**
 * The {@code error.details} array a Gemini API rejection carries, read by {@code @type}. One 429
 * states both which quota ran out and how long to wait, as two entries of this one array, so each
 * reader asks for the type it understands and ignores the rest.
 *
 * <p>The parse-failure log line names the quota failure alone, since players' logs are matched
 * against that text.
 */
@Slf4j
final class AiStudioErrorDetails {

  private AiStudioErrorDetails() {}

  /**
   * Every detail of {@code type} in an error body, in the order the response listed them. Empty
   * when the body carries none, is not the expected shape, or cannot be read at all, so a caller
   * degrades rather than failing the line it was already failing to voice.
   */
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

  /** One string field of a detail, or {@code ""} when it is absent, null, or not a string. */
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
