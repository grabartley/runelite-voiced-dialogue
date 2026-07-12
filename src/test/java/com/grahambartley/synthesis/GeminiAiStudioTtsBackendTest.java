package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.VoicedDialogueConfig;
import com.grahambartley.tts.Pcm;
import com.grahambartley.voice.VoiceManager.NPCGender;
import com.grahambartley.voice.VoiceManager.NPCRace;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Direct Gemini request construction, response decoding, and translation coverage. */
public class GeminiAiStudioTtsBackendTest {

  private static final class TestConfig implements VoicedDialogueConfig {
    String key = "";
    SpokenLanguage language = SpokenLanguage.ENGLISH;
    boolean streaming;

    @Override
    public String googleAiStudioApiKey() {
      return key;
    }

    @Override
    public SpokenLanguage cloudLanguage() {
      return language;
    }

    @Override
    public boolean experimentalStreamingPlayback() {
      return streaming;
    }
  }

  private final Gson gson = new Gson();
  private MockWebServer server;

  @Before
  public void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @After
  public void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  public void availabilityRequiresANonBlankGoogleKey() {
    TestConfig config = new TestConfig();
    GeminiAiStudioTtsBackend backend = backend(config);

    assertFalse(backend.isAvailable());
    config.key = "  ";
    assertFalse(backend.isAvailable());
    config.key = "google-key";
    assertTrue(backend.isAvailable());
    assertEquals(GeminiAiStudioTtsBackend.NO_KEY_NOTICE, backend.missingKeyNotice());
  }

  @Test
  public void sendsCompleteBufferSpeechRequestAndDecodesInlinePcm() throws Exception {
    TestConfig config = new TestConfig();
    config.key = "google-key";
    short[] samples = {0, 16384, -16384};
    server.enqueue(new MockResponse().setBody(audioResponse(samples)));

    Pcm pcm = backend(config).synthesize(request("Hello adventurer"));

    assertNotNull(pcm);
    assertEquals(24_000, pcm.getSampleRate());
    assertEquals(samples.length, pcm.getSamples().length);
    RecordedRequest recorded = server.takeRequest();
    assertEquals("POST", recorded.getMethod());
    assertEquals("/v1beta/models/gemini-3.1-flash-tts-preview:generateContent", recorded.getPath());
    assertEquals("google-key", recorded.getHeader("x-goog-api-key"));
    JsonObject body = new JsonParser().parse(recorded.getBody().readUtf8()).getAsJsonObject();
    assertEquals(
        "AUDIO",
        body.getAsJsonObject("generationConfig")
            .getAsJsonArray("responseModalities")
            .get(0)
            .getAsString());
    assertEquals(
        "Charon",
        body.getAsJsonObject("generationConfig")
            .getAsJsonObject("speechConfig")
            .getAsJsonObject("voiceConfig")
            .getAsJsonObject("prebuiltVoiceConfig")
            .get("voiceName")
            .getAsString());
  }

  @Test
  public void translatesBeforeSynthesizingWhenANonEnglishLanguageIsSelected() throws Exception {
    TestConfig config = new TestConfig();
    config.key = "google-key";
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    server.enqueue(new MockResponse().setBody(textResponse("Bonjour aventurier")));
    server.enqueue(new MockResponse().setBody(audioResponse(new short[] {1})));

    Pcm pcm = backend(config).synthesize(request("Hello adventurer"));

    assertNotNull(pcm);
    assertEquals(
        "/v1beta/models/gemini-3.1-flash-lite:generateContent", server.takeRequest().getPath());
    JsonObject speech =
        new JsonParser().parse(server.takeRequest().getBody().readUtf8()).getAsJsonObject();
    assertEquals(
        "Bonjour aventurier",
        speech
            .getAsJsonArray("contents")
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("parts")
            .get(0)
            .getAsJsonObject()
            .get("text")
            .getAsString());
  }

  @Test
  public void streamingSseDecodesChunksSplitAcrossPcmSamples() throws Exception {
    TestConfig config = new TestConfig();
    config.key = "google-key";
    config.streaming = true;
    byte[] pcm = RawPcmDecoderTest.raw(new short[] {0, 1000, -1000});
    byte[] first = new byte[] {pcm[0], pcm[1], pcm[2]};
    byte[] second = new byte[] {pcm[3], pcm[4], pcm[5]};
    server.enqueue(
        new MockResponse()
            .setBody(
                "data: "
                    + streamingAudioResponse(first, null)
                    + "\n\n"
                    + "data: "
                    + streamingAudioResponse(second, "STOP")
                    + "\n\n"));
    List<Pcm> chunks = new ArrayList<>();

    Pcm complete = backend(config).synthesizeStreaming(request("Hello adventurer"), chunks::add);

    assertNotNull(complete);
    assertEquals(3, complete.getSamples().length);
    assertEquals(2, chunks.size());
    assertEquals(1, chunks.get(0).getSamples().length);
    assertEquals(2, chunks.get(1).getSamples().length);
    assertEquals(
        "/v1beta/models/gemini-3.1-flash-tts-preview:streamGenerateContent?alt=sse",
        server.takeRequest().getPath());
  }

  private GeminiAiStudioTtsBackend backend(TestConfig config) {
    String base = server.url("/v1beta/models/").toString();
    return new GeminiAiStudioTtsBackend(
        new OkHttpClient(),
        config,
        gson,
        base + GeminiAiStudioTtsBackend.MODEL + ":generateContent",
        base + GeminiAiStudioTranslator.MODEL + ":generateContent");
  }

  private static SynthesisRequest request(String text) {
    return new SynthesisRequest(
        text, VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE), Emotion.NEUTRAL);
  }

  private static String audioResponse(short[] samples) {
    JsonObject inlineData = new JsonObject();
    inlineData.addProperty(
        "data", Base64.getEncoder().encodeToString(RawPcmDecoderTest.raw(samples)));
    JsonObject part = new JsonObject();
    part.add("inlineData", inlineData);
    JsonArray parts = new JsonArray();
    parts.add(part);
    return candidateResponse(parts);
  }

  private static String streamingAudioResponse(byte[] audio, String finishReason) {
    JsonObject inlineData = new JsonObject();
    inlineData.addProperty("data", Base64.getEncoder().encodeToString(audio));
    JsonObject part = new JsonObject();
    part.add("inlineData", inlineData);
    JsonArray parts = new JsonArray();
    parts.add(part);
    JsonObject content = new JsonObject();
    content.add("parts", parts);
    JsonObject candidate = new JsonObject();
    candidate.add("content", content);
    if (finishReason != null) {
      candidate.addProperty("finishReason", finishReason);
    }
    JsonArray candidates = new JsonArray();
    candidates.add(candidate);
    JsonObject response = new JsonObject();
    response.add("candidates", candidates);
    return response.toString();
  }

  private static String textResponse(String text) {
    JsonObject part = new JsonObject();
    part.addProperty("text", text);
    JsonArray parts = new JsonArray();
    parts.add(part);
    return candidateResponse(parts);
  }

  private static String candidateResponse(JsonArray parts) {
    JsonObject content = new JsonObject();
    content.add("parts", parts);
    JsonObject candidate = new JsonObject();
    candidate.add("content", content);
    JsonArray candidates = new JsonArray();
    candidates.add(candidate);
    JsonObject response = new JsonObject();
    response.add("candidates", candidates);
    return response.toString();
  }
}
