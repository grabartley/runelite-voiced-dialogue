package com.grahambartley.runelite.voiced.dialogue.speech.spend;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig.TtsProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Counts the billable cloud work done this session, per provider, so {@code ::voicedspend} can
 * answer "what has this session cost me?" without a trip to the provider's dashboard.
 *
 * <p>Only calls that actually reached a provider and came back with something usable are recorded:
 * a memory-cache hit, a disk-cache hit, a deduped in-flight join, and a call that returned no audio
 * all cost nothing and are never counted. The backends record at the point audio is confirmed, past
 * every cache tier, so a replayed line cannot leak into the totals. A speech call recovered on
 * retry counts once, so the totals track lines rather than HTTP attempts.
 *
 * <p>Prefetch synths are counted in their own bucket, so the readout separates lines the player
 * heard from speculative warming. Translation calls are counted separately again, since they bill
 * against a different (much cheaper) model.
 *
 * <p>Where a provider reports what it actually metered, that is recorded instead of inferred:
 * Google AI Studio returns real audio and text token counts per call, which is what its billing is
 * denominated in. A provider that reports nothing leaves those at zero and is costed from its own
 * account balance instead.
 *
 * <p>Session-scoped and memory-only: a fresh instance is built on plugin start and nothing is ever
 * written to disk. Thread-safe, since synthesis, prefetch, and the game thread all touch it.
 */
public final class SpendTracker {

  /** One provider's session totals, as read by the {@code ::voicedspend} readout. */
  @Value
  @Accessors(fluent = true)
  public static class ProviderSpend {
    TtsProvider provider;
    long voicedLines;
    long prefetchedLines;
    long speechCharacters;
    long translationCalls;
    long translationCharacters;

    /** Audio output tokens the provider reported generating, or 0 when it reports none. */
    long audioTokens;

    /** Input tokens the provider reported for the speech calls, or 0 when it reports none. */
    long speechPromptTokens;

    /** Input tokens the provider reported for the translation hop. */
    long translationInputTokens;

    /** Output tokens the provider reported for the translation hop. */
    long translationOutputTokens;
  }

  private final Map<TtsProvider, Counters> byProvider = new ConcurrentHashMap<>();

  /** Records one billable speech call from a provider that reports no token counts. */
  public void recordSpeech(TtsProvider provider, int characters, boolean prefetch) {
    recordSpeech(provider, characters, prefetch, 0, 0);
  }

  /**
   * Records one billable speech call. {@code characters} is the input actually handed to the speech
   * endpoint, after cleaning, capping, translation, and the profile/emotion/pace preamble. {@code
   * audioTokens} and {@code textTokens} are what the provider itself reported metering for the
   * call, and are 0 for a provider that reports nothing.
   */
  public void recordSpeech(
      TtsProvider provider, int characters, boolean prefetch, long audioTokens, long promptTokens) {
    Counters counters = counters(provider);
    if (prefetch) {
      counters.prefetchedLines.incrementAndGet();
    } else {
      counters.voicedLines.incrementAndGet();
    }
    counters.speechCharacters.addAndGet(Math.max(0, characters));
    counters.audioTokens.addAndGet(Math.max(0, audioTokens));
    counters.speechPromptTokens.addAndGet(Math.max(0, promptTokens));
  }

  /** Records one billable translation call from a provider that reports no token counts. */
  public void recordTranslation(TtsProvider provider, int characters) {
    recordTranslation(provider, characters, 0, 0);
  }

  /**
   * Records one billable translation call, the optional first hop before a non-English line. Its
   * tokens are held apart from the speech call's because the hop runs against a different, far
   * cheaper model, so folding the two together would misprice both.
   */
  public void recordTranslation(
      TtsProvider provider, int characters, long inputTokens, long outputTokens) {
    Counters counters = counters(provider);
    counters.translationCalls.incrementAndGet();
    counters.translationCharacters.addAndGet(Math.max(0, characters));
    counters.translationInputTokens.addAndGet(Math.max(0, inputTokens));
    counters.translationOutputTokens.addAndGet(Math.max(0, outputTokens));
  }

  /**
   * The session totals for every provider that did billable work, in provider declaration order.
   * Providers that were never used are absent, so a player who only ever used one provider gets one
   * line rather than a row of zeroes.
   */
  public List<ProviderSpend> snapshot() {
    List<ProviderSpend> out = new ArrayList<>(byProvider.size());
    for (TtsProvider provider : TtsProvider.values()) {
      Counters counters = byProvider.get(provider);
      if (counters != null) {
        out.add(counters.toSpend(provider));
      }
    }
    return out;
  }

  private Counters counters(TtsProvider provider) {
    return byProvider.computeIfAbsent(provider, p -> new Counters());
  }

  private static final class Counters {
    final AtomicLong voicedLines = new AtomicLong();
    final AtomicLong prefetchedLines = new AtomicLong();
    final AtomicLong speechCharacters = new AtomicLong();
    final AtomicLong translationCalls = new AtomicLong();
    final AtomicLong translationCharacters = new AtomicLong();
    final AtomicLong audioTokens = new AtomicLong();
    final AtomicLong speechPromptTokens = new AtomicLong();
    final AtomicLong translationInputTokens = new AtomicLong();
    final AtomicLong translationOutputTokens = new AtomicLong();

    ProviderSpend toSpend(TtsProvider provider) {
      return new ProviderSpend(
          provider,
          voicedLines.get(),
          prefetchedLines.get(),
          speechCharacters.get(),
          translationCalls.get(),
          translationCharacters.get(),
          audioTokens.get(),
          speechPromptTokens.get(),
          translationInputTokens.get(),
          translationOutputTokens.get());
    }
  }
}
