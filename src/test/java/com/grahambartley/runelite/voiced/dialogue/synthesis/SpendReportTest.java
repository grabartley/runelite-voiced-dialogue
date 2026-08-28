package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig.TtsProvider;
import com.grahambartley.runelite.voiced.dialogue.synthesis.SpendTracker.ProviderSpend;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** How the {@code ::voicedspend} command is recognised and how a session reads back in chat. */
public class SpendReportTest {

  private static ProviderSpend openRouter(long voiced, long prefetched, long characters) {
    return new ProviderSpend(TtsProvider.OPENROUTER, voiced, prefetched, characters, 0, 0, 0, 0);
  }

  private static ProviderSpend aiStudio(long voiced, long audioTokens, long textTokens) {
    return new ProviderSpend(
        TtsProvider.GOOGLE_AI_STUDIO, voiced, 0, 400, 0, 0, audioTokens, textTokens);
  }

  private static String only(List<String> lines) {
    assertEquals(1, lines.size());
    return lines.get(0);
  }

  @Test
  public void matchesTheCommandRegardlessOfCaseAndSurroundingSpace() {
    assertTrue(SpendReport.matches("voicedspend"));
    assertTrue(SpendReport.matches("VoicedSpend"));
    assertTrue(SpendReport.matches("  voicedspend  "));
  }

  @Test
  public void ignoresEveryOtherCommand() {
    assertFalse(SpendReport.matches(null));
    assertFalse(SpendReport.matches(""));
    assertFalse(SpendReport.matches("voiced"));
    assertFalse(SpendReport.matches("voicedspends"));
    assertFalse(SpendReport.matches("::voicedspend"));
  }

  @Test
  public void anUntouchedSessionReportsThatNothingWasSpent() {
    String line = only(SpendReport.lines(Collections.emptyList(), null));
    assertTrue(line, line.contains("Nothing has been voiced this session"));
  }

  @Test
  public void nullSnapshotIsTreatedAsAnUntouchedSession() {
    assertEquals(1, SpendReport.lines(null, 0.5).size());
  }

  @Test
  public void openRouterQuotesTheBilledFigureRatherThanAnEstimate() {
    String line =
        only(SpendReport.lines(Collections.singletonList(openRouter(12, 3, 4_821)), 0.1187));

    assertTrue(line, line.startsWith("OpenRouter this session: "));
    assertTrue(line, line.contains("12 lines voiced"));
    assertTrue(line, line.contains("3 prefetched"));
    assertTrue("thousands are grouped: " + line, line.contains("4,821 characters sent"));
    assertTrue("the provider's own figure is quoted: " + line, line.contains("$0.1187"));
    assertTrue("and named as billed, not modelled: " + line, line.contains("billed by OpenRouter"));
    assertFalse("nothing about it is an estimate: " + line, line.toLowerCase().contains("estimat"));
  }

  @Test
  public void openRouterSaysSoWhenTheBalanceCannotBeRead() {
    String line = only(SpendReport.lines(Collections.singletonList(openRouter(4, 0, 900)), null));

    assertTrue("the counts still stand: " + line, line.contains("4 lines voiced"));
    assertTrue("the gap is stated plainly: " + line, line.contains("could not be read"));
    assertFalse(
        "an unreadable balance must never be papered over with a guess: " + line,
        line.contains("$"));
  }

  @Test
  public void aiStudioReportsMeasuredTokensAndLabelsTheConversionAnEstimate() {
    String line =
        only(SpendReport.lines(Collections.singletonList(aiStudio(12, 18_204, 1_208)), null));

    assertTrue(line, line.startsWith("Google AI Studio this session: "));
    assertTrue("thousands are grouped: " + line, line.contains("18,204 audio tokens"));
    assertTrue(line, line.contains("1,208 text tokens"));
    assertTrue("the dollar figure is labelled: " + line, line.contains("Estimated $"));
    assertTrue("and its provenance stated: " + line, line.contains("tokens measured"));
  }

  @Test
  public void aiStudioWithoutTokenCountsReportsCostUnknownRatherThanZero() {
    String line = only(SpendReport.lines(Collections.singletonList(aiStudio(3, 0, 0)), null));

    assertTrue(line, line.contains("3 lines voiced"));
    assertTrue("a missing meter is stated, not costed: " + line, line.contains("cost is unknown"));
    assertFalse("no dollar figure is invented: " + line, line.contains("$"));
  }

  @Test
  public void eachProviderGetsItsOwnLineInDeclarationOrder() {
    List<String> lines =
        SpendReport.lines(Arrays.asList(openRouter(12, 3, 4_821), aiStudio(5, 9_000, 600)), 0.1187);

    assertEquals("one chat line per provider used", 2, lines.size());
    assertTrue(lines.get(0), lines.get(0).startsWith("OpenRouter this session: "));
    assertTrue(lines.get(1), lines.get(1).startsWith("Google AI Studio this session: "));
  }

  @Test
  public void theTranslationBucketAppearsOnlyWhenTranslationHappened() {
    String without =
        only(SpendReport.lines(Collections.singletonList(openRouter(2, 0, 200)), 0.01));
    assertFalse(
        "an English session mentions no translation: " + without, without.contains("translation"));

    ProviderSpend translated = new ProviderSpend(TtsProvider.OPENROUTER, 2, 0, 200, 2, 310, 0, 0);
    String with = only(SpendReport.lines(Collections.singletonList(translated), 0.01));
    assertTrue(with, with.contains("2 translation calls"));
    assertTrue(with, with.contains("310 characters"));
  }

  @Test
  public void aSessionOfPureCacheHitsReadsAsZeroSpend() {
    String line = only(SpendReport.lines(Collections.singletonList(openRouter(0, 0, 0)), 0.0));

    assertTrue(line, line.contains("0 lines voiced"));
    assertTrue(line, line.contains("0 characters sent"));
    assertTrue("a free session reads as free: " + line, line.contains("$0.0000"));
  }

  @Test
  public void aTinyButRealCostIsNotRoundedAwayToNothing() {
    String line = only(SpendReport.lines(Collections.singletonList(openRouter(1, 0, 40)), 0.00002));

    assertTrue(
        "a fraction of a cent still reads as spent: " + line, line.contains("under $0.0001"));
  }
}
