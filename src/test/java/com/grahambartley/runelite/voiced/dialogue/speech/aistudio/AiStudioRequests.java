package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.mockwebserver.RecordedRequest;

final class AiStudioRequests {

  private AiStudioRequests() {}

  static JsonObject body(RecordedRequest request) {
    return new JsonParser().parse(request.getBody().readUtf8()).getAsJsonObject();
  }

  static String text(JsonObject body) {
    return textPart(body).get("text").getAsString();
  }

  static String style(JsonObject body) {
    return textPart(body).getAsJsonObject("speech_metadata").get("style").getAsString();
  }

  private static JsonObject textPart(JsonObject body) {
    return body.getAsJsonArray("contents")
        .get(0)
        .getAsJsonObject()
        .getAsJsonArray("parts")
        .get(0)
        .getAsJsonObject();
  }
}
