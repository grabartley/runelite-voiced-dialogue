package com.grahambartley.synthesis;

/** Adds provider-independent voice direction to a Gemini AUDIO PROFILE prompt. */
final class TtsPromptDirections {

  private TtsPromptDirections() {}

  static String render(CharacterProfile profile, String transcript, String voiceDirection) {
    if (voiceDirection == null || voiceDirection.trim().isEmpty()) {
      return profile == null ? transcript : profile.renderPromptBlock() + transcript;
    }
    String direction = "- Accent: " + voiceDirection.trim() + "\n";
    if (profile == null) {
      return CharacterProfile.GUARD
          + "\n\nDIRECTOR'S NOTES:\n"
          + direction
          + "\n"
          + CharacterProfile.TRANSCRIPT_DIVIDER
          + "\n"
          + transcript;
    }
    return CharacterProfile.GUARD
        + "\n\nAUDIO PROFILE: "
        + profile.name()
        + "\n\nDIRECTOR'S NOTES:\n- Style: "
        + profile.style()
        + "\n"
        + direction
        + "- Pace: "
        + profile.pace()
        + "\n\n"
        + CharacterProfile.TRANSCRIPT_DIVIDER
        + "\n"
        + transcript;
  }
}
