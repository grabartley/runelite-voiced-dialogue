package com.grahambartley.runelite.voiced.dialogue.speech;

import static com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp.HTTP_TOO_MANY_REQUESTS;
import static java.net.HttpURLConnection.HTTP_OK;
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
    assertEquals(
        "line one line two", CloudHttp.bodySnippet("line one\n\nline two".getBytes(), HTTP_OK));

    String snippet = CloudHttp.bodySnippet(filler(400), HTTP_OK);
    assertTrue("an over-long body is marked as truncated", snippet.endsWith("..."));
  }

  @Test
  public void bodySnippetKeepsMoreOfAnErrorBodyThanASuccessfulOne() {
    byte[] body = filler(1_500);

    assertTrue(
        "a quota failure states its cause past the success-path budget",
        CloudHttp.bodySnippet(body, HTTP_TOO_MANY_REQUESTS).length()
            > CloudHttp.bodySnippet(body, HTTP_OK).length());
    assertFalse(
        "an error body within the larger budget is kept whole",
        CloudHttp.bodySnippet(filler(1_500), HTTP_TOO_MANY_REQUESTS).endsWith("..."));
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
