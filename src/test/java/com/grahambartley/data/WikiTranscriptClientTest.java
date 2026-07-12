package com.grahambartley.data;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.grahambartley.dialogue.WikiTranscript;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WikiTranscriptClientTest {

  private MockWebServer server;
  private WikiTranscriptClient client;

  @Before
  public void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client =
        new WikiTranscriptClient(new OkHttpClient(), new Gson(), server.url("/api.php").toString());
  }

  @After
  public void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  public void fetchesTranscriptNamespaceAndParsesWikitext() throws Exception {
    server.enqueue(
        new MockResponse().setBody(pageBody("* '''Hans:''' Hello.\n* '''Player:''' Hi.")));

    WikiTranscript transcript = client.lookup("Hans");

    assertEquals("Hi.", transcript.successors("Hello.").get(0).text());
    RecordedRequest request = server.takeRequest();
    assertEquals("Transcript:Hans", request.getRequestUrl().queryParameter("titles"));
    assertEquals("runelite-voiced-dialogue", request.getHeader("User-Agent"));
  }

  @Test
  public void missingAndFailedPagesAreMisses() {
    server.enqueue(new MockResponse().setBody("{\"query\":{\"pages\":[{\"missing\":true}]}}"));
    assertTrue(client.lookup("Missing").successors("anything").isEmpty());
    server.enqueue(new MockResponse().setResponseCode(HTTP_INTERNAL_ERROR));
    assertNull(client.lookup("Failed"));
  }

  private static String pageBody(String text) {
    JsonObject main = new JsonObject();
    main.addProperty("content", text);
    JsonObject slots = new JsonObject();
    slots.add("main", main);
    JsonObject revision = new JsonObject();
    revision.add("slots", slots);
    JsonArray revisions = new JsonArray();
    revisions.add(revision);
    JsonObject page = new JsonObject();
    page.add("revisions", revisions);
    JsonArray pages = new JsonArray();
    pages.add(page);
    JsonObject query = new JsonObject();
    query.add("pages", pages);
    JsonObject root = new JsonObject();
    root.add("query", query);
    return root.toString();
  }
}
