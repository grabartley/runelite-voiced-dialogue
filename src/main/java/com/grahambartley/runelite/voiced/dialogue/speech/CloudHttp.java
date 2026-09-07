package com.grahambartley.runelite.voiced.dialogue.speech;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Stateless HTTP-side helpers shared by every cloud client class: the common header and media-type
 * constants, the derived keepalive client every backend runs on, and the small response-reading
 * utilities the failure traces need.
 */
public final class CloudHttp {

  /** RFC 6585 Too Many Requests, absent from {@link java.net.HttpURLConnection}'s constants. */
  public static final int HTTP_TOO_MANY_REQUESTS = 429;

  /** Shared empty body for failure traces where no response bytes were (or could be) read. */
  static final byte[] EMPTY_BODY = new byte[0];

  public static final String USER_AGENT = "runelite-voiced-dialogue";

  public static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

  /** Idle connections kept warm so back-to-back lines reuse a pooled connection. */
  private static final int MAX_IDLE_CONNECTIONS = 8;

  /** Max bytes of a non-audio response body echoed into a diagnostic log line. */
  private static final int BODY_SNIPPET_MAX_BYTES = 300;

  /**
   * A provider states why it rejected a call in a details block (quota id, limit, retry hint) that
   * sits well past the first few hundred bytes, so diagnosing one from the log needs more of it.
   */
  private static final int ERROR_BODY_SNIPPET_MAX_BYTES = 2_000;

  private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}+");

  private CloudHttp() {}

  /**
   * Derives a long-lived keepalive client from the injected one (Hub rule: never new an
   * OkHttpClient). {@code newBuilder()} shares the dispatcher cheaply; the derived client gets its
   * own warm connection pool so back-to-back lines skip the TCP/TLS handshake, and its own
   * connect/read/call timeouts without mutating the shared client's globals.
   */
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

  /** Elapsed wall-clock since {@code startNanos}, in whole milliseconds, for a latency trace. */
  static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  public static String headerOrEmpty(Response response, String name) {
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

  public static boolean isNonBlank(String value) {
    return value != null && !value.trim().isEmpty();
  }

  /**
   * First chunk of a response body as printable UTF-8, for diagnosing a non-audio response. An
   * error status keeps more of it, since that body is the diagnosis rather than a hint towards one.
   */
  static String bodySnippet(byte[] bytes, int code) {
    int budget =
        code >= HttpURLConnection.HTTP_BAD_REQUEST
            ? ERROR_BODY_SNIPPET_MAX_BYTES
            : BODY_SNIPPET_MAX_BYTES;
    int n = Math.min(bytes.length, budget);
    String text =
        CONTROL_CHARS
            .matcher(new String(bytes, 0, n, StandardCharsets.UTF_8))
            .replaceAll(" ")
            .trim();
    return bytes.length > n ? text + "..." : text;
  }
}
