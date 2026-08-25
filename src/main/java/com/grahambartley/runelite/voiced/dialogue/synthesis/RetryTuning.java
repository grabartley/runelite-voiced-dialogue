package com.grahambartley.runelite.voiced.dialogue.synthesis;

import java.time.Duration;

/**
 * The network budgets and retry spacing a cloud backend runs under.
 *
 * <p>Each provider gets its own values, because they deliver audio in different ways. Google AI
 * Studio streams a line as it is generated, so a line is audible early and only its full generation
 * has to fit the ceiling. OpenRouter sends nothing until the whole clip exists, so the entire wait
 * for a line arrives as one read and grows with the line's length, which is why its ceiling is far
 * higher and why {@link OpenRouterTtsBackend} narrows it per line rather than letting every request
 * inherit the worst case.
 *
 * <p>Tests build instances directly to shrink the budgets to milliseconds.
 */
final class RetryTuning {

  /** TCP/TLS handshake budget. Short: a slow connect should fail the line fast rather than hang. */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

  /**
   * OpenRouter's ceiling. High because a long line's whole generation is spent before any audio is
   * returned; the per-line budget in {@link OpenRouterTtsBackend#callBudgetFor} is what an
   * individual request actually gets.
   */
  private static final Duration OPEN_ROUTER_CEILING = Duration.ofSeconds(120);

  /**
   * Google AI Studio's ceiling. Its longest measured line (about 500 characters) completes in
   * roughly 14 seconds, so this leaves room for a much longer uncapped line while still releasing a
   * stalled request long before OpenRouter's ceiling would.
   */
  private static final Duration AI_STUDIO_CEILING = Duration.ofSeconds(60);

  /** Base spacing before a backed-off retry of a transient network failure; doubled per attempt. */
  private static final long NETWORK_RETRY_BASE_MILLIS = 400;

  /**
   * Upper bound of the random jitter added to the backoff so concurrent lines do not retry in
   * lockstep.
   */
  private static final long NETWORK_RETRY_JITTER_MILLIS = 250;

  final Duration connectTimeout;
  final Duration readTimeout;
  final Duration callTimeout;
  final long retryBackoffBaseMillis;
  final long retryJitterMillis;

  RetryTuning(
      Duration connectTimeout,
      Duration readTimeout,
      Duration callTimeout,
      long retryBackoffBaseMillis,
      long retryJitterMillis) {
    this.connectTimeout = connectTimeout;
    this.readTimeout = readTimeout;
    this.callTimeout = callTimeout;
    this.retryBackoffBaseMillis = retryBackoffBaseMillis;
    this.retryJitterMillis = retryJitterMillis;
  }

  static RetryTuning openRouter() {
    return ceiling(OPEN_ROUTER_CEILING);
  }

  static RetryTuning googleAiStudio() {
    return ceiling(AI_STUDIO_CEILING);
  }

  /**
   * Budgets bounded by a single ceiling. The per-read budget matches it rather than sitting below
   * it, because both providers can spend the whole wait for a line inside one read, and a shorter
   * per-read budget would kill a line that was about to succeed.
   */
  private static RetryTuning ceiling(Duration ceiling) {
    return new RetryTuning(
        CONNECT_TIMEOUT, ceiling, ceiling, NETWORK_RETRY_BASE_MILLIS, NETWORK_RETRY_JITTER_MILLIS);
  }
}
