package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** How a pronunciation direction composes with (or replaces) a character profile's accent. */
public class TtsPromptDirectionsTest {

  private static final CharacterProfile BANKER =
      new CharacterProfile("Banker", "British RP.", "Professional.", "Normal.");

  @Test
  public void noDirectionKeepsTheProfileBlockUnchanged() {
    assertEquals(
        BANKER.renderPromptBlock() + "Hello", TtsPromptDirections.render(BANKER, "Hello", ""));
  }

  @Test
  public void noDirectionAndNoProfileLeavesTheTranscriptAlone() {
    assertEquals("Hello", TtsPromptDirections.render(null, "Hello", null));
  }

  @Test
  public void directionReplacesTheProfileAccentButKeepsStyleAndPace() {
    String rendered =
        TtsPromptDirections.render(BANKER, "Hello", "Use native Finnish pronunciation.");

    assertTrue(rendered.contains("AUDIO PROFILE: Banker"));
    assertTrue(rendered.contains("- Style: Professional."));
    assertTrue(rendered.contains("- Accent: Use native Finnish pronunciation."));
    assertTrue(rendered.contains("- Pace: Normal."));
    assertFalse(
        "the profile accent must not contradict the direction", rendered.contains("British RP."));
    assertTrue(rendered.endsWith(CharacterProfile.TRANSCRIPT_DIVIDER + "\nHello"));
  }

  @Test
  public void directionWithoutAProfileStillLeadsWithTheGuard() {
    String rendered = TtsPromptDirections.render(null, "Hello", "Use a heavy Boston accent.");

    assertTrue(rendered.startsWith(CharacterProfile.GUARD));
    assertTrue(rendered.contains("- Accent: Use a heavy Boston accent."));
    assertTrue(rendered.endsWith(CharacterProfile.TRANSCRIPT_DIVIDER + "\nHello"));
  }
}
