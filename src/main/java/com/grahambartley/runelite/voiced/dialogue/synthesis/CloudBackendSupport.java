package com.grahambartley.runelite.voiced.dialogue.synthesis;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig.TtsProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The failure-path infrastructure every cloud TTS backend shares: the once-per-session user notice,
 * the standardized {@link CloudSynthTrace} failure logging, the backed-off network retry wait, the
 * speaking-pace clamp, the session spend counters, and the small response/PCM helpers. Each backend
 * composes one instance rather than inheriting, so endpoints, payload shapes, and retry counts stay
 * provider-specific while the plumbing exists exactly once.
 */
@Slf4j
final class CloudBackendSupport {

  /** RFC 6585 Too Many Requests, absent from {@link java.net.HttpURLConnection}'s constants. */
  static final int HTTP_TOO_MANY_REQUESTS = 429;

  /** Speaking pace as a percentage of normal: the default and clamp range. */
  static final int DEFAULT_SPEED_PERCENT = 100;

  private static final int MIN_SPEED_PERCENT = 50;

  private static final int MAX_SPEED_PERCENT = 200;

  /** Max bytes of a non-audio response body echoed into a diagnostic log line. */
  private static final int BODY_SNIPPET_MAX_BYTES = 300;

  /** Shared empty body for failure traces where no response bytes were (or could be) read. */
  static final byte[] EMPTY_BODY = new byte[0];

  private final VoicedDialogueConfig config;
  private final TtsProvider provider;
  private final int maxAttempts;
  private final long networkRetryBaseMillis;
  private final long networkRetryJitterMillis;

  /**
   * Where billable calls are counted. Defaults to a private tracker nobody reads, so a backend
   * built without one (tests, and the moment before the plugin wires its session tracker in) counts
   * into the void rather than needing a null check on every call.
   */
  private SpendTracker spend = new SpendTracker();

  /** One-time user notice hook for cloud failures; defaults to a no-op. */
  private Consumer<String> notice = msg -> {};

  /** Guards the one-time notice so a sustained outage does not spam the chat box. */
  private boolean warned;

  CloudBackendSupport(
      VoicedDialogueConfig config, TtsProvider provider, int maxAttempts, RetryTuning tuning) {
    this.config = config;
    this.provider = provider;
    this.maxAttempts = maxAttempts;
    this.networkRetryBaseMillis = tuning.retryBackoffBaseMillis;
    this.networkRetryJitterMillis = tuning.retryJitterMillis;
  }

  /** Registers a one-time notice hook (e.g. a chat or log message) for cloud failures. */
  void setNotice(Consumer<String> notice) {
    this.notice = notice == null ? msg -> {} : notice;
  }

  /** Points billable-call counting at the plugin's session tracker. */
  void setSpendTracker(SpendTracker spend) {
    this.spend = spend == null ? new SpendTracker() : spend;
  }

  /**
   * Counts one billable speech call for this provider. Called only once audio has actually come
   * back (decoded, or the first chunk fed to the sink), so a cache hit, a deduped join, and a call
   * that returned nothing usable all stay out of the totals, and a line recovered on retry counts
   * once. {@code characters} is the input handed to the speech endpoint, which is what bills.
   */
  void recordSpeechSpend(int characters, boolean prefetch) {
    spend.recordSpeech(provider, characters, prefetch);
  }

  /**
   * The {@code recordSpeechSpend} variant for a provider that reports what it metered, so the
   * session is costed from the provider's own token counts rather than from the input length.
   */
  void recordSpeechSpend(int characters, boolean prefetch, long audioTokens, long textTokens) {
    spend.recordSpeech(provider, characters, prefetch, audioTokens, textTokens);
  }

  /** Counts one billable translation call for this provider, after it returned usable text. */
  void recordTranslationSpend(int characters) {
    spend.recordTranslation(provider, characters);
  }

  /**
   * Surfaces the missing-key notice. Deliberately outside the once-guard: it fires on every
   * unvoiced line and leaves the guard free for a later real failure, with repeat suppression owned
   * by the notice consumer.
   */
  void noticeMissingKey(String message) {
    log.debug(message);
    notice.accept(message);
  }

  void warnOnce(String message) {
    log.debug(message);
    if (!warned) {
      warned = true;
      notice.accept(message);
    }
  }

  /**
   * Logs why a cloud line was rejected in the standardized {@link CloudSynthTrace} shape: reason,
   * attempt, elapsed ms, input length, HTTP status, content type, generation id, and byte count (at
   * warn so it surfaces without debug), plus a short UTF-8 snippet of the body (at info, only in
   * debug mode) since a provider error is typically a JSON/text body, sometimes returned even with
   * HTTP 200. This is what tells rate-limiting/quota/content errors apart from genuinely bad audio,
   * and lets a slow failure be quantified rather than guessed at.
   */
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
      log.info("[TTS cloud] {} body snippet: {}", kind, bodySnippet(bytes));
    }
  }

  /** The {@code logFailure} variant for providers whose responses carry no generation id. */
  void logFailure(
      String kind,
      int attempt,
      long elapsedMs,
      int inputLen,
      int code,
      String message,
      String contentType,
      byte[] bytes) {
    logFailure(kind, attempt, elapsedMs, inputLen, code, message, contentType, "", bytes);
  }

  /** The no-response variant of {@code logFailure} for connect, timeout, and unexpected errors. */
  void logNetworkFailure(String kind, int attempt, long elapsedMs, int inputLen, Exception e) {
    log.warn(
        CloudSynthTrace.failure(
            kind, attempt, maxAttempts, elapsedMs, inputLen, 0, "", "", 0, e.getMessage()));
  }

  /**
   * Spaces a retry after a transient network failure: an exponential base ({@code base * 2^(attempt
   * - 1)}) plus a small random jitter, so a brief blip or a slow generation gets a real second
   * chance without a tight retry storm and concurrent lines do not retry in lockstep. Waits on a
   * synthesis-pool worker, never the game thread, so a second worker keeps serving the next line
   * while this one waits.
   *
   * <p>The wait is a delayed {@link CompletableFuture} joined to completion rather than a sleeping
   * pool thread: the plugin uses no blocking sleep and no thread interrupt (a Hub constraint), and
   * {@link CompletableFuture#join()} parks the worker without either.
   */
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

  /** The configured pace as a percentage of normal, clamped to the supported range. */
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

  /** Elapsed wall-clock since {@code startNanos}, in whole milliseconds, for a latency trace. */
  static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  /** Concatenates the decoded stream chunks into one sample buffer for caching. */
  static float[] flatten(List<float[]> chunks, int totalSamples) {
    float[] out = new float[totalSamples];
    int pos = 0;
    for (float[] chunk : chunks) {
      System.arraycopy(chunk, 0, out, pos, chunk.length);
      pos += chunk.length;
    }
    return out;
  }

  static String headerOrEmpty(Response response, String name) {
    String value = response.header(name);
    return value == null ? "" : value;
  }

  /** Reads a small non-audio error body for diagnostics, tolerating a read failure. */
  static byte[] errorBody(Response response) {
    try {
      ResponseBody body = response.body();
      return body == null ? EMPTY_BODY : body.bytes();
    } catch (IOException e) {
      return EMPTY_BODY;
    }
  }

  static boolean isNonBlank(String value) {
    return value != null && !value.trim().isEmpty();
  }

  /** First chunk of a response body as printable UTF-8, for diagnosing a non-audio response. */
  private static String bodySnippet(byte[] bytes) {
    int n = Math.min(bytes.length, BODY_SNIPPET_MAX_BYTES);
    String text =
        new String(bytes, 0, n, StandardCharsets.UTF_8).replaceAll("\\p{Cntrl}+", " ").trim();
    return bytes.length > n ? text + "..." : text;
  }
}
