package com.grahambartley.runelite.voiced.dialogue.profile;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
public final class CharacterProfile {

  private final String name;
  private final String accent;
  private final String style;
  private final String pace;

  static final String TRANSCRIPT_DIVIDER = "#### TRANSCRIPT";

  static final String GUARD =
      "VOICE ONLY THE TRANSCRIPT BELOW THE DIVIDER, WORD FOR WORD. ADD NO WORDS OF YOUR OWN.";

  public CharacterProfile(String name, String accent, String style, String pace) {
    this.name = stripTrailingOrNull(name);
    this.accent = stripTrailingOrNull(accent);
    this.style = stripTrailingOrNull(style);
    this.pace = stripTrailingOrNull(pace);
  }

  private static String stripTrailingOrNull(String field) {
    return field == null ? null : field.stripTrailing();
  }

  public String renderPromptBlock() {
    return GUARD
        + "\n\n"
        + "AUDIO PROFILE: "
        + name
        + "\n\nDIRECTOR'S NOTES:\n- Style: "
        + style
        + "\n- Accent: "
        + accent
        + "\n- Pace: "
        + pace
        + "\n\n"
        + TRANSCRIPT_DIVIDER
        + "\n";
  }

  public String cacheKey() {
    String joined = name + '' + accent + '' + style + '' + pace;
    return Integer.toHexString(joined.hashCode());
  }
}
