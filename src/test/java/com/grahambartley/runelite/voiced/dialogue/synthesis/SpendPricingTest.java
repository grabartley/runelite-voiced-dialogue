package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Converting Google AI Studio's metered token counts into the estimate the readout quotes. */
public class SpendPricingTest {

  private static final double TOLERANCE = 1e-12;

  @Test
  public void speechBillsAudioAndTextAtTheirOwnPublishedRates() {
    double expected =
        20_000 * SpendPricing.AUDIO_USD_PER_TOKEN + 1_500 * SpendPricing.SPEECH_TEXT_USD_PER_TOKEN;

    assertEquals(expected, SpendPricing.estimateSpeechUsd(20_000, 1_500), TOLERANCE);
  }

  @Test
  public void translationBillsInputAndOutputAtTheirOwnPublishedRates() {
    double expected =
        800 * SpendPricing.TRANSLATION_INPUT_USD_PER_TOKEN
            + 600 * SpendPricing.TRANSLATION_OUTPUT_USD_PER_TOKEN;

    assertEquals(expected, SpendPricing.estimateTranslationUsd(800, 600), TOLERANCE);
  }

  @Test
  public void audioOutputDominatesTheCostOfALine() {
    assertTrue(
        "audio output bills far above text input, so a line's cost tracks how long it speaks",
        SpendPricing.AUDIO_USD_PER_TOKEN > SpendPricing.SPEECH_TEXT_USD_PER_TOKEN * 10);
  }

  @Test
  public void theTranslationHopIsFarCheaperThanSpeechButNotFree() {
    assertTrue(
        "the hop must never dominate the estimate",
        SpendPricing.TRANSLATION_OUTPUT_USD_PER_TOKEN * 10 < SpendPricing.AUDIO_USD_PER_TOKEN);
    assertTrue(
        "cheap is not free, so a translated session is still costed for it",
        SpendPricing.estimateTranslationUsd(800, 600) > 0);
  }

  @Test
  public void aTypicalLineLandsInTheRightOrderOfMagnitude() {
    // ~170 audio tokens is a 100-character line at Gemini's audio token rate, plus a short prompt.
    double usd = SpendPricing.estimateSpeechUsd(170, 40);

    assertTrue("a single line costs fractions of a cent: " + usd, usd > 0.0005 && usd < 0.005);
  }

  @Test
  public void anUntouchedSessionEstimatesNothing() {
    assertEquals(0.0, SpendPricing.estimateSpeechUsd(0, 0), TOLERANCE);
    assertEquals(0.0, SpendPricing.estimateTranslationUsd(0, 0), TOLERANCE);
  }

  @Test
  public void negativeTokenCountsCannotProduceACredit() {
    assertEquals(0.0, SpendPricing.estimateSpeechUsd(-500, -500), TOLERANCE);
    assertEquals(0.0, SpendPricing.estimateTranslationUsd(-500, -500), TOLERANCE);
  }
}
