package com.grahambartley.runelite.voiced.dialogue.speech;

import java.time.Duration;

public final class RetryTuning {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

  private static final Duration OPEN_ROUTER_CEILING = Duration.ofSeconds(120);

  private static final Duration AI_STUDIO_CEILING = Duration.ofSeconds(60);

  private static final long NETWORK_RETRY_BASE_MILLIS = 400;

  private static final long NETWORK_RETRY_JITTER_MILLIS = 250;

  final Duration connectTimeout;
  final Duration readTimeout;
  public final Duration callTimeout;
  final long retryBackoffBaseMillis;
  final long retryJitterMillis;

  public RetryTuning(
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

  public static RetryTuning openRouter() {
    return ceiling(OPEN_ROUTER_CEILING);
  }

  public static RetryTuning googleAiStudio() {
    return ceiling(AI_STUDIO_CEILING);
  }

  private static RetryTuning ceiling(Duration ceiling) {
    return new RetryTuning(
        CONNECT_TIMEOUT, ceiling, ceiling, NETWORK_RETRY_BASE_MILLIS, NETWORK_RETRY_JITTER_MILLIS);
  }
}
