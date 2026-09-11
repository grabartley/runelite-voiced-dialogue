package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig.TtsProvider;
import com.grahambartley.runelite.voiced.dialogue.speech.spend.SpendTracker;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CloudBackendSupport {

  public static final int DEFAULT_SPEED_PERCENT = 100;

  private static final int MIN_SPEED_PERCENT = 50;

  private static final int MAX_SPEED_PERCENT = 200;

  private final VoicedDialogueConfig config;
  private final TtsProvider provider;
  private final int maxAttempts;
  private final long networkRetryBaseMillis;
  private final long networkRetryJitterMillis;

  private SpendTracker spend = new SpendTracker();

  private Consumer<String> notice = msg -> {};

  private final Set<String> warned = ConcurrentHashMap.newKeySet();

  public CloudBackendSupport(
      VoicedDialogueConfig config, TtsProvider provider, int maxAttempts, RetryTuning tuning) {
    this.config = config;
    this.provider = provider;
    this.maxAttempts = maxAttempts;
    this.networkRetryBaseMillis = tuning.retryBackoffBaseMillis;
    this.networkRetryJitterMillis = tuning.retryJitterMillis;
  }

  public void setNotice(Consumer<String> notice) {
    this.notice = notice == null ? msg -> {} : notice;
  }

  public void setSpendTracker(SpendTracker spend) {
    this.spend = spend == null ? new SpendTracker() : spend;
  }

  public void recordSpeechSpend(int characters, boolean prefetch) {
    spend.recordSpeech(provider, characters, prefetch);
  }

  public void recordSpeechSpend(
      int characters, boolean prefetch, long audioTokens, long promptTokens) {
    spend.recordSpeech(provider, characters, prefetch, audioTokens, promptTokens);
  }

  public void recordTranslationSpend(int characters) {
    spend.recordTranslation(provider, characters);
  }

  public void recordTranslationSpend(int characters, long inputTokens, long outputTokens) {
    spend.recordTranslation(provider, characters, inputTokens, outputTokens);
  }

  void noticeMissingKey(String message) {
    log.debug(message);
    notice.accept(message);
  }

  void warnOnce(String message) {
    log.debug(message);
    if (warned.add(message)) {
      notice.accept(message);
    }
  }

  void logFailure(
      String kind,
      int attempt,
      long elapsedMs,
      int inputLen,
      int code,
      String message,
      String contentType,
      String generationId,
      byte[] bytes) {
    log.warn(
        CloudSynthTrace.failure(
            kind,
            attempt,
            maxAttempts,
            elapsedMs,
            inputLen,
            code,
            contentType,
            generationId,
            bytes.length,
            message));
    if (config.debugMode() && bytes.length > 0) {
      log.info("[TTS cloud] {} body snippet: {}", kind, CloudHttp.bodySnippet(bytes));
    }
  }

  void logNetworkFailure(String kind, int attempt, long elapsedMs, int inputLen, Exception e) {
    log.warn(
        CloudSynthTrace.failure(
            kind, attempt, maxAttempts, elapsedMs, inputLen, 0, "", "", 0, e.getMessage()));
  }

  void backoffBeforeNetworkRetry(int attempt) {
    long base = networkRetryBaseMillis << (attempt - 1);
    long jitter =
        networkRetryJitterMillis <= 0
            ? 0
            : ThreadLocalRandom.current().nextLong(networkRetryJitterMillis + 1);
    long delayMillis = base + jitter;
    if (delayMillis <= 0) {
      return;
    }
    CompletableFuture.runAsync(
            () -> {}, CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS))
        .join();
  }

  int speedPercent() {
    int percent = config.speakingPace();
    if (percent < MIN_SPEED_PERCENT) {
      return MIN_SPEED_PERCENT;
    }
    if (percent > MAX_SPEED_PERCENT) {
      return MAX_SPEED_PERCENT;
    }
    return percent;
  }
}
