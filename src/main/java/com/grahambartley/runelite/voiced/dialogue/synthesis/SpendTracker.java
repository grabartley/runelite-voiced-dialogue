package com.grahambartley.runelite.voiced.dialogue.synthesis;

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
  }

  private final Map<TtsProvider, Counters> byProvider = new ConcurrentHashMap<>();

  /**
   * Records one billable speech call: {@code characters} is the input actually handed to the speech
   * endpoint, after cleaning, capping, translation, and the profile/emotion/pace preamble, which is
   * what the provider bills on.
   */
  public void recordSpeech(TtsProvider provider, int characters, boolean prefetch) {
    Counters counters = counters(provider);
    if (prefetch) {
      counters.prefetchedLines.incrementAndGet();
    } else {
      counters.voicedLines.incrementAndGet();
    }
    counters.speechCharacters.addAndGet(Math.max(0, characters));
  }

  /** Records one billable translation call, the optional first hop before a non-English line. */
  public void recordTranslation(TtsProvider provider, int characters) {
    Counters counters = counters(provider);
    counters.translationCalls.incrementAndGet();
    counters.translationCharacters.addAndGet(Math.max(0, characters));
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

  /** Drops every total, so the session starts from zero. */
  public void reset() {
    byProvider.clear();
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

    ProviderSpend toSpend(TtsProvider provider) {
      return new ProviderSpend(
          provider,
          voicedLines.get(),
          prefetchedLines.get(),
          speechCharacters.get(),
          translationCalls.get(),
          translationCharacters.get());
    }
  }
}
