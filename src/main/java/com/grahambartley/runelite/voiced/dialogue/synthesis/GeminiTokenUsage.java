package com.grahambartley.runelite.voiced.dialogue.synthesis;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The token counts Google AI Studio reports for a speech call, read out of the {@code
 * usageMetadata} block of a {@code GenerateContentResponse}.
 *
 * <p>These are the quantities the Gemini API actually metered, so costing a session from them
 * leaves the published rate as the only modelled input. Audio output is the figure that matters: it
 * is billed at roughly twenty times the text rate, so a line's cost tracks how long it takes to
 * speak rather than how long it is to read.
 *
 * <p>Audio tokens come from the {@code AUDIO} entry of {@code candidatesTokensDetails}. A response
 * that omits the per-modality breakdown falls back to {@code candidatesTokenCount}, which for a
 * speech call is audio in its entirety. Anything unparseable yields {@link #NONE}, so a response
 * shape the plugin does not recognise reports no tokens rather than a wrong number.
 */
final class GeminiTokenUsage {

  static final GeminiTokenUsage NONE = new GeminiTokenUsage(0, 0);

  final long audioTokens;
  final long textTokens;

  GeminiTokenUsage(long audioTokens, long textTokens) {
    this.audioTokens = audioTokens;
    this.textTokens = textTokens;
  }

  boolean isEmpty() {
    return audioTokens == 0 && textTokens == 0;
  }

  /**
   * The larger of two readings, field by field. A streamed call reports {@code usageMetadata} on
   * its events as a running total, so the last one carries the whole call; taking the maximum gets
   * that without assuming the events arrive in order, and cannot double-count a cumulative figure.
   */
  GeminiTokenUsage max(GeminiTokenUsage other) {
    if (other == null) {
      return this;
    }
    return new GeminiTokenUsage(
        Math.max(audioTokens, other.audioTokens), Math.max(textTokens, other.textTokens));
  }

  /** The usage reported by one response document, or {@link #NONE} when it reports none. */
  static GeminiTokenUsage parse(Gson gson, String raw) {
    if (raw == null || raw.isEmpty()) {
      return NONE;
    }
    try {
      JsonObject response = gson.fromJson(raw, JsonObject.class);
      JsonObject usage = response == null ? null : response.getAsJsonObject("usageMetadata");
      if (usage == null) {
        return NONE;
      }
      long text = asLong(usage, "promptTokenCount");
      long audio = audioFromDetails(usage);
      if (audio == 0) {
        audio = asLong(usage, "candidatesTokenCount");
      }
      return audio == 0 && text == 0 ? NONE : new GeminiTokenUsage(audio, text);
    } catch (RuntimeException e) {
      return NONE;
    }
  }

  /** Tokens under the {@code AUDIO} modality of {@code candidatesTokensDetails}, or 0. */
  private static long audioFromDetails(JsonObject usage) {
    JsonArray details = usage.getAsJsonArray("candidatesTokensDetails");
    if (details == null) {
      return 0;
    }
    long audio = 0;
    for (JsonElement element : details) {
      JsonObject detail = element.getAsJsonObject();
      if (detail.has("modality")
          && "AUDIO".equalsIgnoreCase(detail.get("modality").getAsString())) {
        audio += asLong(detail, "tokenCount");
      }
    }
    return audio;
  }

  private static long asLong(JsonObject object, String field) {
    if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
      return 0;
    }
    return Math.max(0, object.get(field).getAsLong());
  }
}
