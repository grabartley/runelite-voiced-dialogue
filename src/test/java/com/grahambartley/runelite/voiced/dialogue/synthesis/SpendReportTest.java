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

/** How the {@code ::voicedspend} command is recognised and how a snapshot reads back in chat. */
public class SpendReportTest {

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
    List<String> lines = SpendReport.lines(Collections.emptyList());

    assertEquals(1, lines.size());
    assertTrue(lines.get(0), lines.get(0).contains("Nothing has been voiced this session"));
  }

  @Test
  public void nullSnapshotIsTreatedAsAnUntouchedSession() {
    assertEquals(1, SpendReport.lines(null).size());
  }

  @Test
  public void oneLinePerProviderWithLinesPrefetchesCharactersAndAnEstimate() {
    List<String> lines =
        SpendReport.lines(
            Arrays.asList(
                new ProviderSpend(TtsProvider.OPENROUTER, 12, 3, 4_821, 0, 0),
                new ProviderSpend(TtsProvider.GOOGLE_AI_STUDIO, 5, 0, 500, 0, 0)));

    assertEquals("one chat line per provider used", 2, lines.size());

    String openRouter = lines.get(0);
    assertTrue(openRouter, openRouter.startsWith("OpenRouter this session: "));
    assertTrue(openRouter, openRouter.contains("12 lines voiced"));
    assertTrue(openRouter, openRouter.contains("3 prefetched"));
    assertTrue(
        "thousands are grouped for readability: " + openRouter,
        openRouter.contains("4,821 characters sent"));
    assertTrue("4821 chars at $0.000025 each: " + openRouter, openRouter.contains("$0.1205"));
    assertTrue(
        "the cost is labelled an estimate: " + openRouter, openRouter.contains("an estimate only"));

    assertTrue(lines.get(1), lines.get(1).startsWith("Google AI Studio this session: "));
  }

  @Test
  public void theTranslationBucketAppearsOnlyWhenTranslationHappened() {
    String without =
        SpendReport.lines(
                Collections.singletonList(
                    new ProviderSpend(TtsProvider.OPENROUTER, 2, 0, 200, 0, 0)))
            .get(0);
    assertFalse(
        "an English session mentions no translation: " + without, without.contains("translation"));

    String with =
        SpendReport.lines(
                Collections.singletonList(
                    new ProviderSpend(TtsProvider.OPENROUTER, 2, 0, 200, 2, 310)))
            .get(0);
    assertTrue(with, with.contains("2 translation calls"));
    assertTrue(with, with.contains("310 characters"));
  }

  @Test
  public void aSessionWithOnlyCacheHitsReadsAsZeroSpend() {
    String line =
        SpendReport.lines(
                Collections.singletonList(new ProviderSpend(TtsProvider.OPENROUTER, 0, 0, 0, 0, 0)))
            .get(0);

    assertTrue(line, line.contains("0 lines voiced"));
    assertTrue(line, line.contains("0 characters sent"));
    assertTrue("an untouched provider must not imply a charge: " + line, line.contains("$0.0000"));
  }

  @Test
  public void aTinyButRealCostIsNotRoundedAwayToNothing() {
    String line =
        SpendReport.lines(
                Collections.singletonList(new ProviderSpend(TtsProvider.OPENROUTER, 1, 0, 1, 0, 0)))
            .get(0);

    assertTrue(
        "a fraction of a cent still reads as spent: " + line, line.contains("under $0.0001"));
  }
}
