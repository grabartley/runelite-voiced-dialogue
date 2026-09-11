package com.grahambartley.runelite.voiced.dialogue.speech;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
public final class CloudHttp {

  public static final int HTTP_TOO_MANY_REQUESTS = 429;

  static final byte[] EMPTY_BODY = new byte[0];

  public static final String USER_AGENT = "runelite-voiced-dialogue";

  public static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

  private static final int MAX_IDLE_CONNECTIONS = 8;

  private static final int BODY_SNIPPET_MAX_BYTES = 2_000;

  private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}+");

  private CloudHttp() {}

  public static OkHttpClient deriveClient(
      OkHttpClient base, RetryTuning tuning, Duration keepAlive, boolean pinHttp1) {
    OkHttpClient.Builder builder = base.newBuilder();
    if (pinHttp1) {
      builder.protocols(Collections.singletonList(Protocol.HTTP_1_1));
    }
    return builder
        .connectionPool(
            new ConnectionPool(MAX_IDLE_CONNECTIONS, keepAlive.toMinutes(), TimeUnit.MINUTES))
        .connectTimeout(tuning.connectTimeout)
        .readTimeout(tuning.readTimeout)
        .callTimeout(tuning.callTimeout)
        .retryOnConnectionFailure(true)
        .build();
  }

  static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  public static String headerOrEmpty(Response response, String name) {
    String value = response.header(name);
    return value == null ? "" : value;
  }

  static byte[] errorBody(Response response) {
    try {
      ResponseBody body = response.body();
      return body == null ? EMPTY_BODY : body.bytes();
    } catch (IOException e) {
      return EMPTY_BODY;
    }
  }

  static long retryAfterMillis(Response response) {
    String value = response.header("Retry-After");
    if (!isNonBlank(value)) {
      return 0;
    }
    try {
      long seconds = Long.parseLong(value.trim());
      return seconds <= 0 ? 0 : Math.multiplyExact(seconds, 1_000L);
    } catch (RuntimeException e) {
      log.debug("[TTS cloud] Retry-After '{}' is not a usable wait", value);
      return 0;
    }
  }

  public static boolean isNonBlank(String value) {
    return value != null && !value.trim().isEmpty();
  }

  static String bodySnippet(byte[] bytes) {
    int n = Math.min(bytes.length, BODY_SNIPPET_MAX_BYTES);
    String text =
        CONTROL_CHARS
            .matcher(new String(bytes, 0, n, StandardCharsets.UTF_8))
            .replaceAll(" ")
            .trim();
    return bytes.length > n ? text + "..." : text;
  }
}
