package com.grahambartley.runelite.voiced.dialogue.speech.spend;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig.TtsProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Value;
import lombok.experimental.Accessors;

public final class SpendTracker {

  @Value
  @Accessors(fluent = true)
  public static class ProviderSpend {
    TtsProvider provider;
    long voicedLines;
    long prefetchedLines;
    long speechCharacters;
    long translationCalls;
    long translationCharacters;

    long audioTokens;

    long speechPromptTokens;

    long translationInputTokens;

    long translationOutputTokens;
  }

  private final Map<TtsProvider, Counters> byProvider = new ConcurrentHashMap<>();

  public void recordSpeech(TtsProvider provider, int characters, boolean prefetch) {
    recordSpeech(provider, characters, prefetch, 0, 0);
  }

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

  public void recordTranslation(TtsProvider provider, int characters) {
    recordTranslation(provider, characters, 0, 0);
  }

  public void recordTranslation(
      TtsProvider provider, int characters, long inputTokens, long outputTokens) {
    Counters counters = counters(provider);
    counters.translationCalls.incrementAndGet();
    counters.translationCharacters.addAndGet(Math.max(0, characters));
    counters.translationInputTokens.addAndGet(Math.max(0, inputTokens));
    counters.translationOutputTokens.addAndGet(Math.max(0, outputTokens));
  }

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
