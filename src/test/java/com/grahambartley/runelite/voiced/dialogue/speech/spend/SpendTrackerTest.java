package com.grahambartley.runelite.voiced.dialogue.speech.spend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig.TtsProvider;
import com.grahambartley.runelite.voiced.dialogue.speech.spend.SpendTracker.ProviderSpend;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class SpendTrackerTest {

  @Test
  public void anUnusedProviderIsAbsentFromTheSnapshot() {
    SpendTracker tracker = new SpendTracker();

    assertTrue("nothing recorded -> nothing to report", tracker.snapshot().isEmpty());

    tracker.recordSpeech(TtsProvider.OPENROUTER, 100, false);

    List<ProviderSpend> snapshot = tracker.snapshot();
    assertEquals("only the provider actually used is reported", 1, snapshot.size());
    assertEquals(TtsProvider.OPENROUTER, snapshot.get(0).provider());
  }

  @Test
  public void voicedAndPrefetchedLinesLandInSeparateBuckets() {
    SpendTracker tracker = new SpendTracker();

    tracker.recordSpeech(TtsProvider.OPENROUTER, 40, false);
    tracker.recordSpeech(TtsProvider.OPENROUTER, 60, false);
    tracker.recordSpeech(TtsProvider.OPENROUTER, 25, true);

    ProviderSpend spend = only(tracker);
    assertEquals("two lines the player heard", 2, spend.voicedLines());
    assertEquals("one speculative warm-up", 1, spend.prefetchedLines());
    assertEquals(
        "characters bill the same either way, so they share one total",
        125,
        spend.speechCharacters());
  }

  @Test
  public void translationIsItsOwnBucketAndNeverCountsAsAVoicedLine() {
    SpendTracker tracker = new SpendTracker();

    tracker.recordTranslation(TtsProvider.OPENROUTER, 80);
    tracker.recordSpeech(TtsProvider.OPENROUTER, 95, false);

    ProviderSpend spend = only(tracker);
    assertEquals(1, spend.translationCalls());
    assertEquals(80, spend.translationCharacters());
    assertEquals("the translation hop is not a voiced line", 1, spend.voicedLines());
    assertEquals(
        "translation characters stay out of the speech total", 95, spend.speechCharacters());
  }

  @Test
  public void providersAreCountedIndependentlyAndReportedInDeclarationOrder() {
    SpendTracker tracker = new SpendTracker();

    tracker.recordSpeech(TtsProvider.GOOGLE_AI_STUDIO, 30, false);
    tracker.recordSpeech(TtsProvider.OPENROUTER, 70, true);
    tracker.recordTranslation(TtsProvider.GOOGLE_AI_STUDIO, 10);

    List<ProviderSpend> snapshot = tracker.snapshot();
    assertEquals(2, snapshot.size());
    assertEquals(
        "declaration order, so a switched provider does not reshuffle the readout",
        TtsProvider.OPENROUTER,
        snapshot.get(0).provider());
    assertEquals(TtsProvider.GOOGLE_AI_STUDIO, snapshot.get(1).provider());

    assertEquals(0, snapshot.get(0).voicedLines());
    assertEquals(1, snapshot.get(0).prefetchedLines());
    assertEquals(0, snapshot.get(0).translationCalls());

    assertEquals(1, snapshot.get(1).voicedLines());
    assertEquals(1, snapshot.get(1).translationCalls());
  }

  @Test
  public void negativeCharacterCountsCannotDragTotalsBelowZero() {
    SpendTracker tracker = new SpendTracker();

    tracker.recordSpeech(TtsProvider.OPENROUTER, -50, false);
    tracker.recordTranslation(TtsProvider.OPENROUTER, -10);

    ProviderSpend spend = only(tracker);
    assertEquals(0, spend.speechCharacters());
    assertEquals(0, spend.translationCharacters());
    assertEquals("the call still happened, so it is still counted", 1, spend.voicedLines());
  }

  @Test
  public void reportedTokenCountsAccumulateAlongsideTheLineCounts() {
    SpendTracker tracker = new SpendTracker();

    tracker.recordSpeech(TtsProvider.GOOGLE_AI_STUDIO, 100, false, 1_700, 42);
    tracker.recordSpeech(TtsProvider.GOOGLE_AI_STUDIO, 60, true, 900, 30);

    ProviderSpend spend = only(tracker);
    assertEquals(2_600, spend.audioTokens());
    assertEquals(72, spend.speechPromptTokens());
    assertEquals("warming's tokens count too, they were still metered", 1, spend.prefetchedLines());
  }

  @Test
  public void theTranslationHopsTokensStayApartFromTheSpeechCallsTokens() {
    SpendTracker tracker = new SpendTracker();

    tracker.recordSpeech(TtsProvider.GOOGLE_AI_STUDIO, 100, false, 1_700, 42);
    tracker.recordTranslation(TtsProvider.GOOGLE_AI_STUDIO, 80, 90, 75);

    ProviderSpend spend = only(tracker);
    assertEquals(
        "the hop runs on a cheaper model, so its tokens cannot be pooled",
        1_700,
        spend.audioTokens());
    assertEquals(42, spend.speechPromptTokens());
    assertEquals(90, spend.translationInputTokens());
    assertEquals(75, spend.translationOutputTokens());
    assertEquals(1, spend.translationCalls());
  }

  @Test
  public void aProviderThatReportsNoTokensLeavesThoseTotalsAtZero() {
    SpendTracker tracker = new SpendTracker();

    tracker.recordSpeech(TtsProvider.OPENROUTER, 100, false);

    ProviderSpend spend = only(tracker);
    assertEquals(0, spend.audioTokens());
    assertEquals(0, spend.speechPromptTokens());
    assertEquals("the line is still counted", 1, spend.voicedLines());
  }

  @Test
  public void negativeTokenCountsCannotDragTotalsBelowZero() {
    SpendTracker tracker = new SpendTracker();

    tracker.recordSpeech(TtsProvider.GOOGLE_AI_STUDIO, 100, false, -900, -30);

    ProviderSpend spend = only(tracker);
    assertEquals(0, spend.audioTokens());
    assertEquals(0, spend.speechPromptTokens());
  }

  @Test
  public void concurrentRecordingLosesNoCalls() throws Exception {
    SpendTracker tracker = new SpendTracker();
    int threads = 8;
    int perThread = 250;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    for (int i = 0; i < threads; i++) {
      boolean prefetch = i % 2 == 0;
      pool.execute(
          () -> {
            try {
              start.await();
              for (int n = 0; n < perThread; n++) {
                tracker.recordSpeech(TtsProvider.OPENROUTER, 10, prefetch);
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              done.countDown();
            }
          });
    }
    start.countDown();
    assertTrue("workers finished", done.await(10, TimeUnit.SECONDS));
    pool.shutdownNow();

    ProviderSpend spend = only(tracker);
    assertEquals(threads / 2 * perThread, spend.voicedLines());
    assertEquals(threads / 2 * perThread, spend.prefetchedLines());
    assertEquals(threads * perThread * 10, spend.speechCharacters());
  }

  private static ProviderSpend only(SpendTracker tracker) {
    List<ProviderSpend> snapshot = new ArrayList<>(tracker.snapshot());
    assertEquals("exactly one provider was used", 1, snapshot.size());
    return snapshot.get(0);
  }
}
