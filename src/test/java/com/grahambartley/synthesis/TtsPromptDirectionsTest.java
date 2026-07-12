package com.grahambartley.synthesis;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TtsPromptDirectionsTest {

  @Test
  public void nativePronunciationReplacesAnEnglishProfileAccent() {
    String prompt =
        TtsPromptDirections.render(
            new CharacterProfile("Banker", "British RP", "Professional", "Normal"),
            "[happy] Good day.",
            "Use native Finnish pronunciation. Do not use an English accent.");

    assertTrue(prompt.contains("Use native Finnish pronunciation"));
    assertFalse(prompt.contains("British RP"));
  }

  @Test
  public void plainEnglishRetainsTheProfileAccent() {
    String prompt =
        TtsPromptDirections.render(
            new CharacterProfile("Banker", "Scottish English", "Professional", "Normal"),
            "Good day.",
            "");

    assertTrue(prompt.contains("Scottish English"));
  }
}
