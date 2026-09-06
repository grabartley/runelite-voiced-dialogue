package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;

/**
 * Provider-neutral text rules shared by every cloud TTS provider: the line-length cap, the
 * translation-target decision, the language/quirk combination, and the translator's fixed system
 * prompt. Kept in one place so every provider transforms a line identically, which keeps their
 * synthesis caches and the model-side prompt caches keyed the same way.
 */
public final class CloudTtsText {

  private CloudTtsText() {}

  /**
   * Truncates {@code text} to at most {@code maxChars} characters, cutting at the latest sentence
   * boundary in the kept window, or failing that the latest word boundary, so a capped line still
   * ends cleanly rather than mid-word. A non-positive cap or an already-short line is returned
   * unchanged. The sentence boundary is only honoured past the halfway mark so an early period does
   * not collapse a long line down to a fragment.
   */
  static String capLength(String text, int maxChars) {
    if (text == null || maxChars <= 0 || text.length() <= maxChars) {
      return text;
    }
    String window = text.substring(0, maxChars);
    for (int i = window.length() - 1; i >= maxChars / 2; i--) {
      char c = window.charAt(i);
      if (c == '.' || c == '!' || c == '?') {
        return window.substring(0, i + 1).trim();
      }
    }
    int lastSpace = window.lastIndexOf(' ');
    if (lastSpace > 0) {
      return window.substring(0, lastSpace).trim();
    }
    return window.trim();
  }

  /** A target language other than English (case-insensitive, blank treated as English). */
  static boolean needsTranslation(String language) {
    return language != null
        && !language.trim().isEmpty()
        && !language.trim().equalsIgnoreCase("English");
  }

  /**
   * The spoken language actually requested of the model for a line: the configured language with
   * the speaker-class Speaking Style appended (Player style for the player's own lines, NPC style
   * for everything else), so "English" plus a Gen Z style becomes an "English Gen Z slang" target
   * that routes through the translation hop and is rewritten in that style. A blank language
   * defaults to English; the no-op style leaves the language untouched, so a class set to None
   * skips the hop while the other class can still be styled.
   */
  static String effectiveSpokenLanguage(VoicedDialogueConfig config, SynthesisRequest request) {
    VoicedDialogueConfig.SpeakingStyle style =
        request.player() ? config.cloudPlayerSpeakingStyle() : config.cloudNpcSpeakingStyle();
    return combineLanguage(config.cloudLanguage().label(), style);
  }

  /**
   * Appends a non-empty quirk phrase to the (blank-safe) base language, e.g. "French pirate speak".
   */
  static String combineLanguage(String language, VoicedDialogueConfig.SpeakingStyle quirk) {
    String base = language == null || language.trim().isEmpty() ? "English" : language.trim();
    if (quirk == null || quirk.isNone()) {
      return base;
    }
    return base + " " + quirk.phrase();
  }

  /**
   * The fixed translator system prompt for a target language, shared by every provider's
   * translation hop so they rewrite lines identically. The wording is deterministic in {@code
   * language} (no timestamps, names, or per-line text) so the same language always produces the
   * byte-identical prefix the model's prompt cache keys on.
   */
  public static String translatorSystemPrompt(String language) {
    return "You are a translation engine for an Old School RuneScape dialogue voice plugin."
        + " Translate the user's line into "
        + language
        + ". Preserve proper nouns, character names, place names, item names, and RuneScape"
        + " terminology exactly as written. Output only the translation, with no quotes, notes,"
        + " explanations, or preamble.";
  }
}
