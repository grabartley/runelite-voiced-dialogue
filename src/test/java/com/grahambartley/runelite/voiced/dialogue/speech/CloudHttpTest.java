package com.grahambartley.runelite.voiced.dialogue.speech;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

/** The stateless HTTP helpers shared by the cloud client classes. */
public class CloudHttpTest {

  @Test
  public void isNonBlankRequiresANonWhitespaceCharacter() {
    assertFalse(CloudHttp.isNonBlank(null));
    assertFalse(CloudHttp.isNonBlank(""));
    assertFalse(CloudHttp.isNonBlank("   "));
    assertTrue(CloudHttp.isNonBlank("key"));
    assertTrue(CloudHttp.isNonBlank("  key  "));
  }

  @Test
  public void headerOrEmptyNeverReturnsNull() {
    Response response = response().newBuilder().header("Content-Type", "audio/pcm").build();
    assertEquals("audio/pcm", CloudHttp.headerOrEmpty(response, "Content-Type"));
    assertEquals("", CloudHttp.headerOrEmpty(response, "X-Missing"));
  }

  @Test
  public void errorBodyReadsTheBytesAndTreatsAMissingBodyAsEmpty() {
    Response withBody =
        response()
            .newBuilder()
            .body(ResponseBody.create(MediaType.parse("text/plain"), "quota exceeded"))
            .build();
    assertArrayEquals("quota exceeded".getBytes(), CloudHttp.errorBody(withBody));
    assertArrayEquals(new byte[0], CloudHttp.errorBody(response()));
  }

  @Test
  public void bodySnippetFlattensControlCharactersAndMarksTruncation() {
    assertEquals("line one line two", CloudHttp.bodySnippet("line one\n\nline two".getBytes()));

    assertTrue(
        "an over-long body is marked as truncated",
        CloudHttp.bodySnippet(filler(4_000)).endsWith("..."));
  }

  @Test
  public void bodySnippetKeepsAWholeQuotaFailureBody() {
    // A Gemini quota rejection runs ~1.4KB, with the fields naming the cause at the far end.
    assertFalse(
        "the field naming the cause must survive the log",
        CloudHttp.bodySnippet(filler(1_500)).endsWith("..."));
  }

  @Test
  public void retryAfterIsReadOnlyWhenItNamesAUsableWait() {
    assertEquals(30_000, CloudHttp.retryAfterMillis(withRetryAfter("30")));
    assertEquals(
        "whitespace is not a malformation",
        5_000,
        CloudHttp.retryAfterMillis(withRetryAfter(" 5 ")));
    assertEquals("no header means the caller decides", 0, CloudHttp.retryAfterMillis(response()));
    assertEquals(
        "an HTTP-date is not read against a client clock",
        0,
        CloudHttp.retryAfterMillis(withRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT")));
    assertEquals(0, CloudHttp.retryAfterMillis(withRetryAfter("soon")));
    assertEquals(
        "a wait already over is no wait", 0, CloudHttp.retryAfterMillis(withRetryAfter("0")));
    assertEquals(0, CloudHttp.retryAfterMillis(withRetryAfter("-30")));
    assertEquals(
        "an unrepresentable wait is unusable",
        0,
        CloudHttp.retryAfterMillis(withRetryAfter(String.valueOf(Long.MAX_VALUE))));
  }

  private static Response withRetryAfter(String value) {
    return response().newBuilder().header("Retry-After", value).build();
  }

  private static byte[] filler(int length) {
    byte[] bytes = new byte[length];
    Arrays.fill(bytes, (byte) 'a');
    return bytes;
  }

  private static Response response() {
    return new Response.Builder()
        .request(new Request.Builder().url("http://localhost/").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .build();
  }
}
