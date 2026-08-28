package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig.TtsProvider;
import org.junit.Test;

/** The per-character cost estimates and the arithmetic the readout quotes. */
public class SpendPricingTest {

  private static final double TOLERANCE = 1e-12;

  @Test
  public void aTypicalLineMatchesTheAverageQuotedInTheReadme() {
    for (TtsProvider provider : TtsProvider.values()) {
      assertEquals(
          "a 100-character line costs about $0.0025 to voice, the README's average",
          0.0025,
          SpendPricing.estimateUsd(provider, 100, 0),
          1e-6);
    }
  }

  @Test
  public void speechAndTranslationCharactersAreBilledAtTheirOwnRates() {
    double expected =
        1_000 * SpendPricing.speechUsdPerCharacter(TtsProvider.OPENROUTER)
            + 400 * SpendPricing.translationUsdPerCharacter(TtsProvider.OPENROUTER);

    assertEquals(expected, SpendPricing.estimateUsd(TtsProvider.OPENROUTER, 1_000, 400), TOLERANCE);
  }

  @Test
  public void translationIsOrdersOfMagnitudeCheaperThanSpeech() {
    for (TtsProvider provider : TtsProvider.values()) {
      assertTrue(
          "the translation hop must never dominate the estimate",
          SpendPricing.translationUsdPerCharacter(provider) * 100
              < SpendPricing.speechUsdPerCharacter(provider));
    }
  }

  @Test
  public void everyProviderHasANonZeroRateForBothCallKinds() {
    for (TtsProvider provider : TtsProvider.values()) {
      assertTrue(provider + " speech rate", SpendPricing.speechUsdPerCharacter(provider) > 0);
      assertTrue(
          provider + " translation rate", SpendPricing.translationUsdPerCharacter(provider) > 0);
    }
  }

  @Test
  public void anUntouchedSessionEstimatesNothing() {
    assertEquals(0.0, SpendPricing.estimateUsd(TtsProvider.OPENROUTER, 0, 0), TOLERANCE);
  }

  @Test
  public void negativeCharacterCountsCannotProduceACredit() {
    assertEquals(0.0, SpendPricing.estimateUsd(TtsProvider.OPENROUTER, -500, -500), TOLERANCE);
  }
}
