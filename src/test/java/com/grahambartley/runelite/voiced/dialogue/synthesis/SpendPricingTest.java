package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Converting Google AI Studio's metered token counts into the estimate the readout quotes. */
public class SpendPricingTest {

  private static final double TOLERANCE = 1e-12;

  @Test
  public void audioAndTextTokensAreBilledAtTheirOwnPublishedRates() {
    double expected =
        20_000 * SpendPricing.AUDIO_USD_PER_TOKEN + 1_500 * SpendPricing.TEXT_USD_PER_TOKEN;

    assertEquals(expected, SpendPricing.estimateUsd(20_000, 1_500), TOLERANCE);
  }

  @Test
  public void audioOutputDominatesTheCostOfALine() {
    assertTrue(
        "audio output bills far above text input, so a line's cost tracks how long it speaks",
        SpendPricing.AUDIO_USD_PER_TOKEN > SpendPricing.TEXT_USD_PER_TOKEN * 10);
  }

  @Test
  public void aTypicalLineLandsInTheRightOrderOfMagnitude() {
    // ~170 audio tokens is a 100-character line at Gemini's audio token rate, plus a short prompt.
    double usd = SpendPricing.estimateUsd(170, 40);

    assertTrue("a single line costs fractions of a cent: " + usd, usd > 0.0005 && usd < 0.005);
  }

  @Test
  public void anUntouchedSessionEstimatesNothing() {
    assertEquals(0.0, SpendPricing.estimateUsd(0, 0), TOLERANCE);
  }

  @Test
  public void negativeTokenCountsCannotProduceACredit() {
    assertEquals(0.0, SpendPricing.estimateUsd(-500, -500), TOLERANCE);
  }
}
