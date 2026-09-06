package com.grahambartley.runelite.voiced.dialogue.synthesis;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The token counts Google AI Studio reports for a call, read out of the {@code usageMetadata} block
 * of a {@code GenerateContentResponse}.
 *
 * <p>These are the quantities the Gemini API actually metered, so costing a session from them
 * leaves the published rate as the only modelled input.
 *
 * <p>Output is split by kind because the two are billed nowhere near alike: audio output costs
 * roughly twenty times text output, so a speech call's tokens must never be read as a translation
 * call's or the estimate is wrong by more than an order of magnitude. {@link #forSpeech} therefore
 * treats output as audio and {@link #forText} treats it as text, rather than one parser guessing
 * from the response shape.
 */
final class GeminiTokenUsage {

  static final GeminiTokenUsage NONE = new GeminiTokenUsage(0, 0, 0);

  /** Input tokens the call consumed. */
  final long promptTokens;

  /** Audio output tokens, for a speech call. */
  final long audioTokens;

  /** Text output tokens, for a translation call. */
  final long textTokens;

  GeminiTokenUsage(long promptTokens, long audioTokens, long textTokens) {
    this.promptTokens = promptTokens;
    this.audioTokens = audioTokens;
    this.textTokens = textTokens;
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
        Math.max(promptTokens, other.promptTokens),
        Math.max(audioTokens, other.audioTokens),
        Math.max(textTokens, other.textTokens));
  }

  /**
   * Usage for a speech call, whose output is audio: taken from the {@code AUDIO} entry of {@code
   * candidatesTokensDetails}, falling back to {@code candidatesTokenCount} when the response omits
   * the per-modality breakdown, since a speech call's candidates are audio in their entirety.
   */
  static GeminiTokenUsage forSpeech(Gson gson, String raw) {
    return forSpeech(parse(gson, raw));
  }

  /**
   * The {@link #forSpeech(Gson, String)} variant for a caller that already parsed the response
   * document (to extract its audio), so a multi-megabyte body is never parsed twice.
   */
  static GeminiTokenUsage forSpeech(JsonObject response) {
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

  /** Usage for a text call (the translation hop), whose output bills at the text rate. */
  static GeminiTokenUsage forText(Gson gson, String raw) {
    JsonObject usage = usageMetadata(parse(gson, raw));
    if (usage == null) {
      return NONE;
    }
    return of(asLong(usage, "promptTokenCount"), 0, asLong(usage, "candidatesTokenCount"));
  }

  private static GeminiTokenUsage of(long prompt, long audio, long text) {
    return prompt == 0 && audio == 0 && text == 0
        ? NONE
        : new GeminiTokenUsage(prompt, audio, text);
  }

  /** A response document parsed, or {@code null} when it is unreadable. */
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

  /** The {@code usageMetadata} block of a response document, or {@code null} when unreadable. */
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

  /** Tokens under one modality of {@code candidatesTokensDetails}, or 0. */
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
