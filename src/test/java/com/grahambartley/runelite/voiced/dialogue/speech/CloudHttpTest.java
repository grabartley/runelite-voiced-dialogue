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
    // The rejection that started this: ~1.4KB, with the quota details at the far end of it.
    assertFalse(
        "the field naming the cause must survive the log",
        CloudHttp.bodySnippet(filler(1_500)).endsWith("..."));
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
