package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.profile.ProfanityFilter;
import java.util.regex.Pattern;

public final class DialogueTextCleaner {

  private static final Pattern MARKUP = Pattern.compile("<[^>]+>");

  private final ProfanityFilter profanityFilter;

  public DialogueTextCleaner(ProfanityFilter profanityFilter) {
    this.profanityFilter = profanityFilter;
  }

  public String clean(String raw) {
    return profanityFilter.mask(MARKUP.matcher(raw).replaceAll("").trim());
  }
}
