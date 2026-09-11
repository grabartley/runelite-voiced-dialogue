package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class AiStudioTokenUsage {

  static final AiStudioTokenUsage NONE = new AiStudioTokenUsage(0, 0, 0);

  final long promptTokens;

  final long audioTokens;

  final long textTokens;

  AiStudioTokenUsage(long promptTokens, long audioTokens, long textTokens) {
    this.promptTokens = promptTokens;
    this.audioTokens = audioTokens;
    this.textTokens = textTokens;
  }

  AiStudioTokenUsage max(AiStudioTokenUsage other) {
    if (other == null) {
      return this;
    }
    return new AiStudioTokenUsage(
        Math.max(promptTokens, other.promptTokens),
        Math.max(audioTokens, other.audioTokens),
        Math.max(textTokens, other.textTokens));
  }

  static AiStudioTokenUsage forSpeech(Gson gson, String raw) {
    return forSpeech(parse(gson, raw));
  }

  static AiStudioTokenUsage forSpeech(JsonObject response) {
    JsonObject usage = usageMetadata(response);
    if (usage == null) {
      return NONE;
    }
    long audio = modalityTokens(usage, "AUDIO");
    if (audio == 0) {
      audio = asLong(usage, "candidatesTokenCount");
    }
    return of(asLong(usage, "promptTokenCount"), audio, 0);
  }

  static AiStudioTokenUsage forText(Gson gson, String raw) {
    JsonObject usage = usageMetadata(parse(gson, raw));
    if (usage == null) {
      return NONE;
    }
    return of(asLong(usage, "promptTokenCount"), 0, asLong(usage, "candidatesTokenCount"));
  }

  private static AiStudioTokenUsage of(long prompt, long audio, long text) {
    return prompt == 0 && audio == 0 && text == 0
        ? NONE
        : new AiStudioTokenUsage(prompt, audio, text);
  }

  private static JsonObject parse(Gson gson, String raw) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    try {
      return gson.fromJson(raw, JsonObject.class);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static JsonObject usageMetadata(JsonObject response) {
    if (response == null) {
      return null;
    }
    try {
      return response.getAsJsonObject("usageMetadata");
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static long modalityTokens(JsonObject usage, String modality) {
    try {
      JsonArray details = usage.getAsJsonArray("candidatesTokensDetails");
      if (details == null) {
        return 0;
      }
      long total = 0;
      for (JsonElement element : details) {
        JsonObject detail = element.getAsJsonObject();
        if (detail.has("modality")
            && modality.equalsIgnoreCase(detail.get("modality").getAsString())) {
          total += asLong(detail, "tokenCount");
        }
      }
      return total;
    } catch (RuntimeException e) {
      return 0;
    }
  }

  private static long asLong(JsonObject object, String field) {
    if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
      return 0;
    }
    try {
      return Math.max(0, object.get(field).getAsLong());
    } catch (RuntimeException e) {
      return 0;
    }
  }
}
