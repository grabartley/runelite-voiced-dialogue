package com.grahambartley;

import com.grahambartley.synthesis.ProfanityFilter;

/**
 * The single spoken-text chokepoint (#149): strips HTML-ish markup, trims, then masks profanity
 * unconditionally. Every voiced source (NPC dialogue, player options, attacker-controlled public
 * chat) funnels through {@link #clean}, so masking covers both backends with no toggle to bypass.
 */
final class DialogueTextCleaner {

  private final ProfanityFilter profanityFilter;

  DialogueTextCleaner(ProfanityFilter profanityFilter) {
    this.profanityFilter = profanityFilter;
  }

  /** Strips tags and trims, then masks profanity. Never returns {@code null} for non-null input. */
  String clean(String raw) {
    return profanityFilter.mask(raw.replaceAll("<[^>]+>", "").trim());
  }
}
