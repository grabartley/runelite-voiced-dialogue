package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig.TtsProvider;
import org.junit.Test;

/** Who keeps OpenRouter when the shipped default becomes Google AI Studio, and who does not. */
public class ProviderDefaultPolicyTest {

  @Test
  public void existingOpenRouterPlayerIsPinnedToOpenRouter() {
    assertTrue(
        "a player with a key but no explicit choice was relying on the old default",
        ProviderDefaultPolicy.shouldPinToOpenRouter(null, "sk-or-abc"));
  }

  @Test
  public void newPlayerGetsTheNewDefault() {
    assertFalse(
        "no key and no choice means a fresh install, which should get Google AI Studio",
        ProviderDefaultPolicy.shouldPinToOpenRouter(null, ""));
    assertFalse(
        "a blank key is not evidence of prior use",
        ProviderDefaultPolicy.shouldPinToOpenRouter(null, "   "));
    assertFalse(
        "an absent key is not evidence of prior use",
        ProviderDefaultPolicy.shouldPinToOpenRouter(null, null));
  }

  @Test
  public void anExplicitChoiceIsNeverOverwritten() {
    assertFalse(
        "a player already pinned to OpenRouter is left alone",
        ProviderDefaultPolicy.shouldPinToOpenRouter(TtsProvider.OPENROUTER.name(), "sk-or-abc"));
    assertFalse(
        "a player who chose AI Studio is not dragged back by an old OpenRouter key",
        ProviderDefaultPolicy.shouldPinToOpenRouter(
            TtsProvider.GOOGLE_AI_STUDIO.name(), "sk-or-abc"));
  }
}
