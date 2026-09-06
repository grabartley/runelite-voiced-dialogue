package com.grahambartley.runelite.voiced.dialogue.speech;

import static com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp.HTTP_TOO_MANY_REQUESTS;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Request shape, system-prompt stability, content extraction, and graceful failure. */
public class OpenRouterTranslatorTest {

  private MockWebServer server;
  private OkHttpClient client;
  private final Gson gson = new Gson();
  private final VoicedDialogueConfig config = new VoicedDialogueConfig() {};

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

  private OpenRouterTranslator translator() {
    return new OpenRouterTranslator(
        client, config, gson, server.url("/api/v1/chat/completions").toString());
  }

  @Test
  public void translatesAndReturnsTrimmedContent() throws Exception {
    server.enqueue(
        new MockResponse()
            .setResponseCode(HTTP_OK)
            .setBody(TestFixtures.chatResponse("  Bonjour  ")));

    String result = translator().translate("Hello", "French", "sk-or-abc");

    assertEquals("Bonjour", result);

    RecordedRequest recorded = server.takeRequest();
    assertEquals("POST", recorded.getMethod());
    assertEquals("Bearer sk-or-abc", recorded.getHeader("Authorization"));
    assertEquals(
        "the OpenRouter app name is attributed",
        "RuneLite Voiced Dialogue",
        recorded.getHeader("X-Title"));
    JsonObject body = new JsonParser().parse(recorded.getBody().readUtf8()).getAsJsonObject();
    assertEquals("google/gemini-3.1-flash-lite", body.get("model").getAsString());
    assertEquals(
        "throughput routing applies to the translation hop too",
        "throughput",
        body.getAsJsonObject("provider").get("sort").getAsString());
    JsonArray messages = body.getAsJsonArray("messages");
    assertEquals("system prompt then user line", 2, messages.size());
    assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
    assertEquals(
        "the per-line text is the user message, keeping the system prefix stable",
        "Hello",
        messages.get(1).getAsJsonObject().get("content").getAsString());
  }

  @Test
  public void nonSuccessReturnsNull() {
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_TOO_MANY_REQUESTS).setBody("rate limited"));
    assertNull(translator().translate("Hello", "French", "sk-or-abc"));
  }

  @Test
  public void unparseableOrEmptyBodyReturnsNull() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody("not json"));
    assertNull(translator().translate("Hello", "French", "sk-or-abc"));
  }

  @Test
  public void emptyInputIsReturnedWithoutCallingTheNetwork() {
    assertEquals("", translator().translate("", "French", "sk-or-abc"));
    assertEquals("no HTTP request for empty input", 0, server.getRequestCount());
  }
}
