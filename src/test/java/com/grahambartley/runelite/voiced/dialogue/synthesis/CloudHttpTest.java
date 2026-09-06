package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    byte[] big = new byte[400];
    java.util.Arrays.fill(big, (byte) 'a');
    String snippet = CloudHttp.bodySnippet(big);
    assertTrue("an over-long body is marked as truncated", snippet.endsWith("..."));
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
