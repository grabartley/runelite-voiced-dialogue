package com.grahambartley.runelite.voiced.dialogue.speech;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Reading an OpenRouter key's all-time credit usage, and failing quietly when it cannot be read.
 */
public class OpenRouterUsageClientTest {

  private static final double TOLERANCE = 1e-9;

  private MockWebServer server;
  private OkHttpClient client;
  private final Gson gson = new Gson();

  @Before
  public void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client = new OkHttpClient();
  }

  @After
  public void tearDown() throws Exception {
    server.shutdown();
  }

  private OpenRouterUsageClient usageClient() {
    return new OpenRouterUsageClient(client, gson, server.url("/api/v1/key").toString());
  }

  @Test
  public void readsUsageFromTheKeyEndpointWithBearerAuth() throws Exception {
    server.enqueue(
        new MockResponse()
            .setResponseCode(HTTP_OK)
            .setBody("{\"data\":{\"label\":\"tts\",\"usage\":4.6187,\"limit\":null}}"));

    Double usage = usageClient().fetchUsage("sk-or-secret");

    assertEquals(4.6187, usage, TOLERANCE);
    RecordedRequest recorded = server.takeRequest();
    assertEquals("GET", recorded.getMethod());
    assertEquals("/api/v1/key", recorded.getPath());
    assertEquals("Bearer sk-or-secret", recorded.getHeader("Authorization"));
  }

  @Test
  public void aBlankKeyIsNeverSentOverTheWire() {
    assertNull(usageClient().fetchUsage(null));
    assertNull(usageClient().fetchUsage(""));
    assertNull(usageClient().fetchUsage("   "));
    assertEquals("no request is made without a key", 0, server.getRequestCount());
  }

  @Test
  public void aRejectedKeyReadsAsUnknownRatherThanZero() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_UNAUTHORIZED).setBody("bad key"));

    assertNull(usageClient().fetchUsage("sk-or-secret"));
  }

  @Test
  public void aBodyWithoutUsageReadsAsUnknown() {
    OpenRouterUsageClient usageClient = usageClient();

    assertNull(usageClient.extractUsage(null));
    assertNull(usageClient.extractUsage(""));
    assertNull(usageClient.extractUsage("not json"));
    assertNull(usageClient.extractUsage("{}"));
    assertNull(usageClient.extractUsage("{\"data\":{}}"));
    assertNull(usageClient.extractUsage("{\"data\":{\"usage\":null}}"));
  }

  @Test
  public void aZeroUsageKeyIsAReadingNotAFailure() {
    assertEquals(0.0, usageClient().extractUsage("{\"data\":{\"usage\":0}}"), TOLERANCE);
  }
}
