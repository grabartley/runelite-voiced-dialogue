package com.grahambartley.runelite.voiced.dialogue.speech.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GeminiTranslationModelTest {

  @Test
  public void openRouterIdIsTheGeminiIdUnderTheGoogleNamespace() {
    assertEquals(
        "google/" + GeminiTranslationModel.GEMINI_MODEL_ID, GeminiTranslationModel.MODEL_ID);
  }

  @Test
  public void geminiIdCarriesNoProviderNamespace() {
    assertFalse(
        "the bare Gemini API name must not be namespaced",
        GeminiTranslationModel.GEMINI_MODEL_ID.contains("/"));
  }

  @Test
  public void bothIdsArePopulated() {
    assertTrue(GeminiTranslationModel.GEMINI_MODEL_ID.length() > 0);
    assertTrue(GeminiTranslationModel.MODEL_ID.length() > 0);
  }
}
