package com.grahambartley.runelite.voiced.dialogue.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Turning two readings of an OpenRouter key's all-time usage into this session's real spend. */
public class OpenRouterCreditMeterTest {

  private static final double TOLERANCE = 1e-9;

  @Test
  public void spendIsTheRiseAboveTheSessionBaseline() {
    OpenRouterCreditMeter meter = new OpenRouterCreditMeter();
    meter.recordBaseline("sk-or-abc", 4.5000);

    assertEquals(0.1187, meter.spentSince("sk-or-abc", 4.6187), TOLERANCE);
  }

  @Test
  public void spendIsUnknownUntilABaselineIsTaken() {
    OpenRouterCreditMeter meter = new OpenRouterCreditMeter();

    assertNull(
        "an unread balance must never be reported as a free session",
        meter.spentSince("sk-or-abc", 4.6187));
    assertFalse(meter.hasBaselineFor("sk-or-abc"));
  }

  @Test
  public void anUnreadableBaselineIsNotBanked() {
    OpenRouterCreditMeter meter = new OpenRouterCreditMeter();
    meter.recordBaseline("sk-or-abc", null);

    assertFalse("a failed read leaves the meter unarmed", meter.hasBaselineFor("sk-or-abc"));
    assertNull(meter.spentSince("sk-or-abc", 4.6187));
  }

  @Test
  public void aSecondBaselineForTheSameKeyNeverErasesSpendAlreadyMade() {
    OpenRouterCreditMeter meter = new OpenRouterCreditMeter();
    meter.recordBaseline("sk-or-abc", 4.5000);
    meter.recordBaseline("sk-or-abc", 4.6000);

    assertEquals(
        "a re-warm mid-session must not rebase and hide what was already spent",
        0.1187,
        meter.spentSince("sk-or-abc", 4.6187),
        TOLERANCE);
  }

  @Test
  public void aSwappedKeyIsMeasuredFromItsOwnBaseline() {
    OpenRouterCreditMeter meter = new OpenRouterCreditMeter();
    meter.recordBaseline("sk-or-abc", 4.5000);

    assertNull(
        "usage on a different key is a different running total, so it cannot be subtracted",
        meter.spentSince("sk-or-xyz", 90.0));

    meter.recordBaseline("sk-or-xyz", 90.0);
    assertEquals(0.25, meter.spentSince("sk-or-xyz", 90.25), TOLERANCE);
  }

  @Test
  public void aBalanceBelowTheBaselineClampsToZeroRatherThanGoingNegative() {
    OpenRouterCreditMeter meter = new OpenRouterCreditMeter();
    meter.recordBaseline("sk-or-abc", 4.5000);

    assertEquals(0.0, meter.spentSince("sk-or-abc", 4.0000), TOLERANCE);
  }

  @Test
  public void anUnreadableCurrentBalanceLeavesSpendUnknown() {
    OpenRouterCreditMeter meter = new OpenRouterCreditMeter();
    meter.recordBaseline("sk-or-abc", 4.5000);

    assertNull(meter.spentSince("sk-or-abc", null));
  }

  @Test
  public void resetStartsAFreshMeasurement() {
    OpenRouterCreditMeter meter = new OpenRouterCreditMeter();
    meter.recordBaseline("sk-or-abc", 4.5000);
    meter.reset();

    assertFalse(meter.hasBaselineFor("sk-or-abc"));
    meter.recordBaseline("sk-or-abc", 4.6187);
    assertEquals(0.0, meter.spentSince("sk-or-abc", 4.6187), TOLERANCE);
  }

  @Test
  public void keysAreComparedIgnoringSurroundingWhitespace() {
    OpenRouterCreditMeter meter = new OpenRouterCreditMeter();
    meter.recordBaseline("  sk-or-abc  ", 4.5000);

    assertTrue(meter.hasBaselineFor("sk-or-abc"));
    assertEquals(0.1187, meter.spentSince("sk-or-abc", 4.6187), TOLERANCE);
  }
}
