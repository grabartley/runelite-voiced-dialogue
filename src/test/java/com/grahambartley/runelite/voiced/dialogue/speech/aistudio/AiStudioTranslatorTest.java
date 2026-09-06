package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudTtsText;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Request shape, content extraction, and graceful failure of the Gemini translation hop. */
public class AiStudioTranslatorTest {

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

  private AiStudioTranslator translator() {
    return new AiStudioTranslator(
        client,
        config,
        gson,
        server.url("/v1beta/models/" + AiStudioTranslator.MODEL + ":generateContent").toString());
  }

  private static String translationResponse(String content) {
    JsonObject textPart = new JsonObject();
    textPart.addProperty("text", content);
    JsonArray parts = new JsonArray();
    parts.add(textPart);
    JsonObject contentObj = new JsonObject();
    contentObj.add("parts", parts);
    JsonObject candidate = new JsonObject();
    candidate.add("content", contentObj);
    JsonArray candidates = new JsonArray();
    candidates.add(candidate);
    JsonObject body = new JsonObject();
    body.add("candidates", candidates);
    return body.toString();
  }

  @Test
  public void translatesAndReturnsTrimmedContent() throws Exception {
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_OK).setBody(translationResponse("  Bonjour  ")));

    AiStudioTranslator.Translation result = translator().translate("Hello", "French", "AIza-abc");

    assertEquals("Bonjour", result.text);

    RecordedRequest recorded = server.takeRequest();
    assertEquals("POST", recorded.getMethod());
    assertEquals("AIza-abc", recorded.getHeader("x-goog-api-key"));
    JsonObject body = new JsonParser().parse(recorded.getBody().readUtf8()).getAsJsonObject();
    String systemPrompt =
        body.getAsJsonObject("systemInstruction")
            .getAsJsonArray("parts")
            .get(0)
            .getAsJsonObject()
            .get("text")
            .getAsString();
    assertEquals(
        "the fixed system prompt is shared with the OpenRouter hop",
        CloudTtsText.translatorSystemPrompt("French"),
        systemPrompt);
    assertEquals(
        "the per-line text is the user content, keeping the system prefix stable",
        "Hello",
        body.getAsJsonArray("contents")
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("parts")
            .get(0)
            .getAsJsonObject()
            .get("text")
            .getAsString());
  }

  @Test
  public void emptyInputIsReturnedWithoutCallingTheNetwork() {
    AiStudioTranslator.Translation result = translator().translate("", "French", "AIza-abc");

    assertEquals("", result.text);
    assertEquals("no HTTP request for empty input", 0, server.getRequestCount());
  }

  @Test
  public void nonSuccessReturnsNull() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_INTERNAL_ERROR).setBody("boom"));
    assertNull(translator().translate("Hello", "French", "AIza-abc"));
  }

  @Test
  public void unparseableOrEmptyBodyReturnsNull() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody("not json"));
    assertNull(translator().translate("Hello", "French", "AIza-abc"));
  }
}
