package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.tts.Pcm;
import com.grahambartley.runelite.voiced.dialogue.voice.VoiceManager.NPCGender;
import com.grahambartley.runelite.voiced.dialogue.voice.VoiceManager.NPCRace;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * HTTP path, headers, Gemini JSON body, base64 audio decode, SSE streaming, availability gating,
 * cache variant, translation hop, and graceful failure of the direct Google AI Studio backend.
 */
public class GeminiAiStudioTtsBackendTest {

  /** Config with a settable key, pace, and language; everything else uses interface defaults. */
  private static final class TestConfig implements VoicedDialogueConfig {
    String key = "";
    int speedPercent = 100;
    VoicedDialogueConfig.SpokenLanguage language = VoicedDialogueConfig.SpokenLanguage.ENGLISH;

    @Override
    public String googleAiStudioApiKey() {
      return key;
    }

    @Override
    public int speakingPace() {
      return speedPercent;
    }

    @Override
    public VoicedDialogueConfig.SpokenLanguage cloudLanguage() {
      return language;
    }
  }

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

  private GeminiAiStudioTtsBackend backend(TestConfig config) {
    // Point speech and translation at the mock server while keeping the real header, JSON body,
    // SSE decode, and error logic; millisecond retry budgets so retry paths run without real
    // waits.
    return new GeminiAiStudioTtsBackend(
        client,
        config,
        gson,
        server
            .url("/v1beta/models/" + GeminiAiStudioTtsBackend.MODEL + ":generateContent")
            .toString(),
        server
            .url("/v1beta/models/" + GeminiAiStudioTranslator.MODEL + ":generateContent")
            .toString(),
        new RetryTuning(
            java.time.Duration.ofMillis(500),
            java.time.Duration.ofMillis(500),
            java.time.Duration.ofSeconds(1),
            10,
            0));
  }

  private static SynthesisRequest req() {
    return new SynthesisRequest(
        "Hello & welcome", VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE), Emotion.NEUTRAL);
  }

  /** A complete Gemini JSON response carrying the samples as one base64 inlineData part. */
  private static String audioResponse(short[] samples) {
    return responseDocument(RawPcmDecoderTest.raw(samples), "STOP");
  }

  /** A complete response whose usageMetadata reports what the API metered for the call. */
  private static String audioResponseWithUsage(short[] samples, long audioTokens, long textTokens) {
    return withUsage(audioResponse(samples), audioTokens, textTokens);
  }

  /** Folds a usageMetadata block into an existing response document. */
  private static String withUsage(String document, long audioTokens, long textTokens) {
    JsonObject detail = new JsonObject();
    detail.addProperty("modality", "AUDIO");
    detail.addProperty("tokenCount", audioTokens);
    JsonArray details = new JsonArray();
    details.add(detail);
    JsonObject usage = new JsonObject();
    usage.addProperty("promptTokenCount", textTokens);
    usage.addProperty("candidatesTokenCount", audioTokens);
    usage.add("candidatesTokensDetails", details);
    JsonObject body = new JsonParser().parse(document).getAsJsonObject();
    body.add("usageMetadata", usage);
    return body.toString();
  }

  private static String responseDocument(byte[] audioBytes, String finishReason) {
    JsonObject inlineData = new JsonObject();
    inlineData.addProperty("mimeType", "audio/L16;codec=pcm;rate=24000");
    inlineData.addProperty("data", Base64.getEncoder().encodeToString(audioBytes));
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
    JsonObject body = new JsonObject();
    body.add("candidates", candidates);
    return body.toString();
  }

  /** One SSE event per audio chunk, the last carrying the finish reason. */
  private static String sseBody(List<byte[]> chunks, String finishReason) {
    StringBuilder sse = new StringBuilder();
    for (int i = 0; i < chunks.size(); i++) {
      String reason = i == chunks.size() - 1 ? finishReason : null;
      sse.append("data: ").append(responseDocument(chunks.get(i), reason)).append("\n\n");
    }
    return sse.toString();
  }

  private static String geminiTranslation(String content) {
    JsonObject textPart = new JsonObject();
    textPart.addProperty("text", content);
    JsonArray parts = new JsonArray();
    parts.add(textPart);
    JsonObject contentObj = new JsonObject();
    contentObj.add("parts", parts);
    JsonObject candidate = new JsonObject();
    candidate.add("content", contentObj);
    candidate.addProperty("finishReason", "STOP");
    JsonArray candidates = new JsonArray();
    candidates.add(candidate);
    JsonObject body = new JsonObject();
    body.add("candidates", candidates);
    return body.toString();
  }

  @Test
  public void availabilityRequiresKey() {
    TestConfig config = new TestConfig();
    assertFalse("blank key -> unavailable", backend(config).isAvailable());

    config.key = "   ";
    assertFalse("whitespace-only key -> unavailable", backend(config).isAvailable());

    config.key = "AIza-abc";
    assertTrue("a key set -> available", backend(config).isAvailable());
  }

  @Test
  public void missingKeyNoticeNamesTheProvider() {
    assertEquals(
        GeminiAiStudioTtsBackend.NO_KEY_NOTICE, backend(new TestConfig()).missingKeyNotice());
    assertTrue(GeminiAiStudioTtsBackend.NO_KEY_NOTICE.contains("Google AI Studio"));
  }

  @Test
  public void missingKeyFailsWithoutARequest() {
    Pcm pcm = backend(new TestConfig()).synthesize(req());

    assertNull("no key -> line not voiced", pcm);
    assertEquals("no HTTP call is made without a key", 0, server.getRequestCount());
  }

  @Test
  public void sendsApiKeyHeaderAndGeminiBody() throws Exception {
    TestConfig config = new TestConfig();
    config.key = "AIza-secret";
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_OK).setBody(audioResponse(new short[] {1})));

    backend(config).synthesize(req());

    RecordedRequest recorded = server.takeRequest();
    assertEquals("POST", recorded.getMethod());
    assertEquals(
        "/v1beta/models/" + GeminiAiStudioTtsBackend.MODEL + ":generateContent",
        recorded.getPath());
    assertEquals("AIza-secret", recorded.getHeader("x-goog-api-key"));
    assertTrue(
        "a JSON content type is sent",
        recorded.getHeader("Content-Type").startsWith("application/json"));
    assertNotNull("a User-Agent is sent", recorded.getHeader("User-Agent"));

    JsonObject body = new JsonParser().parse(recorded.getBody().readUtf8()).getAsJsonObject();
    String text =
        body.getAsJsonArray("contents")
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("parts")
            .get(0)
            .getAsJsonObject()
            .get("text")
            .getAsString();
    assertEquals("Hello & welcome", text);
    JsonObject generationConfig = body.getAsJsonObject("generationConfig");
    assertEquals(
        "AUDIO", generationConfig.getAsJsonArray("responseModalities").get(0).getAsString());
    String voiceName =
        generationConfig
            .getAsJsonObject("speechConfig")
            .getAsJsonObject("voiceConfig")
            .getAsJsonObject("prebuiltVoiceConfig")
            .get("voiceName")
            .getAsString();
    assertEquals(
        "the voice is whatever the shared map resolves for the spec",
        new GeminiVoiceMap().voiceFor(req().voice()),
        voiceName);
    assertFalse(
        "plain English sends no language code",
        generationConfig.getAsJsonObject("speechConfig").has("languageCode"));
  }

  @Test
  public void successfulResponseDecodesBase64PcmAt24k() {
    TestConfig config = new TestConfig();
    config.key = "AIza-abc";
    short[] samples = {0, 16384, -16384, 32767};
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(audioResponse(samples)));

    Pcm pcm = backend(config).synthesize(req());

    assertNotNull("a 200 with base64 PCM yields audio", pcm);
    assertEquals(24_000, pcm.getSampleRate());
    assertEquals(samples.length, pcm.getSamples().length);
  }

  @Test
  public void emotionTagAndProfileBlockLeadTheSpokenText() throws Exception {
    TestConfig config = new TestConfig();
    config.key = "AIza-abc";
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_OK).setBody(audioResponse(new short[] {1})));
    CharacterProfile profile =
        new CharacterProfile(
            "Troll",
            "British English, South London Brixton accent.",
            "A huge, slow, simple-minded troll.",
            "Slow and heavy.");

    backend(config)
        .synthesize(
            new SynthesisRequest(
                "You no take candle!",
                VoiceSpec.npc(NPCRace.TROLL, NPCGender.MALE),
                Emotion.ANGRY,
                profile));

    JsonObject body =
        new JsonParser().parse(server.takeRequest().getBody().readUtf8()).getAsJsonObject();
    String text =
        body.getAsJsonArray("contents")
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("parts")
            .get(0)
            .getAsJsonObject()
            .get("text")
            .getAsString();
    assertTrue(
        "the profile block leads and the emotion-tagged transcript follows",
        text.endsWith("[angry] You no take candle!"));
    assertTrue("the profile block is present", text.startsWith(profile.renderPromptBlock()));
  }

  @Test
  public void nonDefaultPaceBecomesAPromptDirection() throws Exception {
    TestConfig config = new TestConfig();
    config.key = "AIza-abc";
    config.speedPercent = 150;
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_OK).setBody(audioResponse(new short[] {1})));

    backend(config).synthesize(req());

    JsonObject body =
        new JsonParser().parse(server.takeRequest().getBody().readUtf8()).getAsJsonObject();
    String text =
        body.getAsJsonArray("contents")
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("parts")
            .get(0)
            .getAsJsonObject()
            .get("text")
            .getAsString();
    assertTrue(
        "a non-default pace has no API parameter, so it is a prompt direction",
        text.startsWith("SPEAKING PACE: 150% of normal."));
  }

  @Test
  public void non2xxFailsTheLineGracefully() {
    TestConfig config = new TestConfig();
    config.key = "AIza-abc";
    List<String> notices = new ArrayList<>();
    GeminiAiStudioTtsBackend backend = backend(config);
    backend.setNotice(notices::add);
    server.enqueue(new MockResponse().setResponseCode(HTTP_INTERNAL_ERROR).setBody("boom"));

    assertNull(backend.synthesize(req()));
    assertEquals("one notice for the failure", 1, notices.size());
    assertTrue(notices.get(0).contains("HTTP 500"));
  }

  @Test
  public void rateLimitOpensTheThrottleWindowAndNamesTheQuota() {
    TestConfig config = new TestConfig();
    config.key = "AIza-abc";
    List<String> notices = new ArrayList<>();
    GeminiAiStudioTtsBackend backend = backend(config);
    backend.setNotice(notices::add);
    server.enqueue(
        new MockResponse()
            .setResponseCode(CloudBackendSupport.HTTP_TOO_MANY_REQUESTS)
            .setBody("quota"));

    assertNull(backend.synthesize(req()));
    assertTrue("a 429 opens the prefetch back-off window", backend.isThrottled());
    assertEquals(GeminiAiStudioTtsBackend.QUOTA_NOTICE, notices.get(0));
  }

  @Test
  public void emptyAudioIsRetriedOnceThenFails() {
    TestConfig config = new TestConfig();
    config.key = "AIza-abc";
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody("{\"candidates\":[]}"));
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody("{\"candidates\":[]}"));

    assertNull(backend(config).synthesize(req()));
    assertEquals("one retry for a transient empty response", 2, server.getRequestCount());
  }

  @Test
  public void emptyAudioRecoversOnTheRetry() {
    TestConfig config = new TestConfig();
    config.key = "AIza-abc";
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody("{\"candidates\":[]}"));
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_OK).setBody(audioResponse(new short[] {1, 2})));

    Pcm pcm = backend(config).synthesize(req());

    assertNotNull("the transient empty response is recovered by the retry", pcm);
    assertEquals(2, server.getRequestCount());
  }

  @Test
  public void streamingFeedsTheSinkPerChunkAndReturnsTheWholeLineForCaching() throws Exception {
    TestConfig config = new TestConfig();
    config.key = "AIza-abc";
    short[] first = {1, 2, 3};
    short[] second = {4, 5};
    List<byte[]> chunks = new ArrayList<>();
    chunks.add(RawPcmDecoderTest.raw(first));
    chunks.add(RawPcmDecoderTest.raw(second));
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(sseBody(chunks, "STOP")));

    List<float[]> sunk = new ArrayList<>();
    Pcm pcm = backend(config).synthesizeStreaming(req(), (samples, rate) -> sunk.add(samples));

    assertEquals("each SSE audio part reaches the sink as its own chunk", 2, sunk.size());
    assertEquals(first.length, sunk.get(0).length);
    assertEquals(second.length, sunk.get(1).length);
    assertNotNull("a complete stream returns the whole line for caching", pcm);
    assertEquals(first.length + second.length, pcm.getSamples().length);

    RecordedRequest recorded = server.takeRequest();
    assertEquals(
        "the streaming path posts to the SSE streamGenerateContent endpoint",
        "/v1beta/models/" + GeminiAiStudioTtsBackend.MODEL + ":streamGenerateContent?alt=sse",
        recorded.getPath());
  }

  @Test
  public void streamingChunkSplitAcrossSamplesIsReassembled() {
    TestConfig config = new TestConfig();
    config.key = "AIza-abc";
    // One 16-bit sample split across two SSE events: an odd leading byte must be carried, never
    // dropped or played as a half sample.
    byte[] whole = RawPcmDecoderTest.raw(new short[] {1, 2, 3});
    byte[] head = new byte[3];
    byte[] tail = new byte[3];
    System.arraycopy(whole, 0, head, 0, 3);
    System.arraycopy(whole, 3, tail, 0, 3);
    List<byte[]> chunks = new ArrayList<>();
    chunks.add(head);
    chunks.add(tail);
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(sseBody(chunks, "STOP")));

    List<Float> sunk = new ArrayList<>();
    Pcm pcm =
        backend(config)
            .synthesizeStreaming(
                req(),
                (samples, rate) -> {
                  for (float sample : samples) {
                    sunk.add(sample);
                  }
                });

    assertNotNull(pcm);
    assertEquals("all three samples survive the split", 3, pcm.getSamples().length);
    assertEquals(3, sunk.size());
  }

  @Test
  public void incompleteStreamPlaysButIsNotReturnedForCaching() {
    TestConfig config = new TestConfig();
    config.key = "AIza-abc";
    List<byte[]> chunks = new ArrayList<>();
    chunks.add(RawPcmDecoderTest.raw(new short[] {1, 2, 3}));
    // No STOP finish reason: the stream was cut before the model finished the line.
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(sseBody(chunks, null)));

    List<float[]> sunk = new ArrayList<>();
    Pcm pcm = backend(config).synthesizeStreaming(req(), (samples, rate) -> sunk.add(samples));

    assertEquals("what arrived still played", 1, sunk.size());
    assertNull("an incomplete stream is never handed back for caching", pcm);
  }

  @Test
  public void emptyStreamIsRetriedOnceThenFails() {
    TestConfig config = new TestConfig();
    config.key = "AIza-abc";
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(""));
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(""));

    List<float[]> sunk = new ArrayList<>();
    Pcm pcm = backend(config).synthesizeStreaming(req(), (samples, rate) -> sunk.add(samples));

    assertNull(pcm);
    assertTrue(sunk.isEmpty());
    assertEquals("one retry for a transient empty stream", 2, server.getRequestCount());
  }

  @Test
  public void cacheVariantFoldsInModelAndVoiceSoRendersNeverCollide() {
    GeminiAiStudioTtsBackend backend = backend(new TestConfig());

    SynthesisRequest humanMale =
        new SynthesisRequest("a", VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE), Emotion.NEUTRAL);
    SynthesisRequest elfFemale =
        new SynthesisRequest("a", VoiceSpec.npc(NPCRace.ELF, NPCGender.FEMALE), Emotion.NEUTRAL);

    String variant = backend.cacheVariant(humanMale);
    assertTrue(
        "the variant carries the AI Studio model id",
        variant.contains(GeminiAiStudioTtsBackend.MODEL));
    assertTrue(
        "the variant carries the resolved Gemini voice",
        variant.contains(new GeminiVoiceMap().voiceFor(humanMale.voice())));
    assertNotEquals(
        "two specs that map to different voices never share a variant",
        backend.cacheVariant(humanMale),
        backend.cacheVariant(elfFemale));
  }

  @Test
  public void nonEnglishLanguageRoutesThroughTheGeminiTranslationHop() throws Exception {
    TestConfig config = new TestConfig();
    config.key = "AIza-abc";
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_OK).setBody(geminiTranslation("Bonjour")));
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_OK).setBody(audioResponse(new short[] {1})));

    Pcm pcm = backend(config).synthesize(req());
    assertNotNull(pcm);

    RecordedRequest translation = server.takeRequest();
    assertEquals(
        "the first hop is the Flash Lite translation model",
        "/v1beta/models/" + GeminiAiStudioTranslator.MODEL + ":generateContent",
        translation.getPath());
    JsonObject translationBody =
        new JsonParser().parse(translation.getBody().readUtf8()).getAsJsonObject();
    String systemPrompt =
        translationBody
            .getAsJsonObject("systemInstruction")
            .getAsJsonArray("parts")
            .get(0)
            .getAsJsonObject()
            .get("text")
            .getAsString();
    assertTrue(
        "the shared translation prompt targets the language", systemPrompt.contains("French"));

    RecordedRequest speech = server.takeRequest();
    JsonObject speechBody = new JsonParser().parse(speech.getBody().readUtf8()).getAsJsonObject();
    String spokenText =
        speechBody
            .getAsJsonArray("contents")
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("parts")
            .get(0)
            .getAsJsonObject()
            .get("text")
            .getAsString();
    assertEquals("the translated text is what gets voiced", "Bonjour", spokenText);
    assertEquals(
        "a translated line carries the BCP-47 code so it is pronounced natively",
        "fr-FR",
        speechBody
            .getAsJsonObject("generationConfig")
            .getAsJsonObject("speechConfig")
            .get("languageCode")
            .getAsString());
  }

  @Test
  public void failedTranslationFailsTheLineWithoutASpeechCall() {
    TestConfig config = new TestConfig();
    config.key = "AIza-abc";
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    server.enqueue(new MockResponse().setResponseCode(HTTP_INTERNAL_ERROR).setBody("boom"));

    assertNull(backend(config).synthesize(req()));
    assertEquals("no speech call after a failed translation", 1, server.getRequestCount());
  }

  @Test
  public void languageFragmentReKeysTranslatedLinesInTheCacheVariant() {
    TestConfig config = new TestConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    GeminiAiStudioTtsBackend backend = backend(config);

    assertTrue(
        "a translated line folds the target language into the cache variant",
        backend.cacheVariant(req()).contains("french"));

    config.language = VoicedDialogueConfig.SpokenLanguage.ENGLISH;
    assertFalse(
        "plain English keeps the pre-translation key",
        backend.cacheVariant(req()).contains("english"));
  }

  @Test
  public void streamedSamplesMatchTheBufferedDecodeOfTheSameBytes() {
    TestConfig config = new TestConfig();
    config.key = "AIza-abc";
    short[] samples = {100, -200, 300, -400, 500};
    List<byte[]> chunks = new ArrayList<>();
    chunks.add(RawPcmDecoderTest.raw(samples));
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(sseBody(chunks, "STOP")));

    Pcm streamed = backend(config).synthesizeStreaming(req(), (chunk, rate) -> {});

    assertNotNull(streamed);
    assertArrayEquals(
        "streamed decode is byte-identical to a whole-buffer decode",
        RawPcmDecoder.decode(RawPcmDecoderTest.raw(samples), 24_000).getSamples(),
        streamed.getSamples(),
        0f);
  }

  @Test
  public void aVoicedLineCountsOnceAgainstTheCharactersActuallySent() throws Exception {
    TestConfig config = new TestConfig();
    config.key = "aistudio-key";
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_OK).setBody(audioResponse(new short[] {1, 2})));
    SpendTracker spend = new SpendTracker();
    GeminiAiStudioTtsBackend backend = backend(config);
    backend.setSpendTracker(spend);

    assertNotNull(backend.synthesize(req()));

    JsonObject sent =
        new JsonParser().parse(server.takeRequest().getBody().readUtf8()).getAsJsonObject();
    String input =
        sent.getAsJsonArray("contents")
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("parts")
            .get(0)
            .getAsJsonObject()
            .get("text")
            .getAsString();

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals(VoicedDialogueConfig.TtsProvider.GOOGLE_AI_STUDIO, recorded.provider());
    assertEquals(1, recorded.voicedLines());
    assertEquals(0, recorded.prefetchedLines());
    assertEquals(
        "the counted characters are the input the endpoint bills on",
        input.length(),
        recorded.speechCharacters());
  }

  @Test
  public void aPrefetchedLineCountsAsWarmingRatherThanAsAVoicedLine() {
    TestConfig config = new TestConfig();
    config.key = "aistudio-key";
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_OK).setBody(audioResponse(new short[] {1, 2})));
    SpendTracker spend = new SpendTracker();
    GeminiAiStudioTtsBackend backend = backend(config);
    backend.setSpendTracker(spend);

    backend.synthesize(req().asPrefetch());

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals(0, recorded.voicedLines());
    assertEquals(1, recorded.prefetchedLines());
    assertTrue("warming still costs characters", recorded.speechCharacters() > 0);
  }

  @Test
  public void aStreamedLineCountsOnceWhenTheFirstAudioArrives() {
    TestConfig config = new TestConfig();
    config.key = "aistudio-key";
    List<byte[]> chunks =
        Arrays.asList(
            RawPcmDecoderTest.raw(new short[] {1, 2}), RawPcmDecoderTest.raw(new short[] {3, 4}));
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(sseBody(chunks, "STOP")));
    SpendTracker spend = new SpendTracker();
    GeminiAiStudioTtsBackend backend = backend(config);
    backend.setSpendTracker(spend);

    backend.synthesizeStreaming(req(), (samples, rate) -> {});

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals("a multi-chunk stream is still one billable line", 1, recorded.voicedLines());
  }

  @Test
  public void aFailedLineThatReturnsNoAudioCostsNothing() {
    TestConfig config = new TestConfig();
    config.key = "aistudio-key";
    server.enqueue(new MockResponse().setResponseCode(HTTP_UNAUTHORIZED).setBody("bad key"));
    SpendTracker spend = new SpendTracker();
    GeminiAiStudioTtsBackend backend = backend(config);
    backend.setSpendTracker(spend);

    assertNull(backend.synthesize(req()));

    assertTrue("a rejected line never reaches the readout", spend.snapshot().isEmpty());
  }

  @Test
  public void aLineNeverSentForWantOfAKeyCostsNothing() {
    SpendTracker spend = new SpendTracker();
    GeminiAiStudioTtsBackend backend = backend(new TestConfig());
    backend.setSpendTracker(spend);

    assertNull(backend.synthesize(req()));

    assertTrue(spend.snapshot().isEmpty());
  }

  @Test
  public void theTranslationHopIsCountedInItsOwnBucket() {
    TestConfig config = new TestConfig();
    config.key = "aistudio-key";
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_OK).setBody(geminiTranslation("Bonjour")));
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_OK).setBody(audioResponse(new short[] {1, 2})));
    SpendTracker spend = new SpendTracker();
    GeminiAiStudioTtsBackend backend = backend(config);
    backend.setSpendTracker(spend);

    assertNotNull(backend.synthesize(req()));

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals("one translation call", 1, recorded.translationCalls());
    assertEquals(
        "translation bills on the source line",
        "Hello & welcome".length(),
        recorded.translationCharacters());
    assertEquals("the spoken line is still counted once", 1, recorded.voicedLines());
  }

  @Test
  public void aVoicedLineBanksTheTokenCountsTheApiActuallyReported() {
    TestConfig config = new TestConfig();
    config.key = "aistudio-key";
    server.enqueue(
        new MockResponse()
            .setResponseCode(HTTP_OK)
            .setBody(audioResponseWithUsage(new short[] {1, 2}, 1_700, 42)));
    SpendTracker spend = new SpendTracker();
    GeminiAiStudioTtsBackend backend = backend(config);
    backend.setSpendTracker(spend);

    assertNotNull(backend.synthesize(req()));

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals("audio tokens are measured, not modelled", 1_700, recorded.audioTokens());
    assertEquals(42, recorded.speechPromptTokens());
  }

  @Test
  public void aStreamedLineBanksTheRunningTotalFromTheFinalEvent() {
    TestConfig config = new TestConfig();
    config.key = "aistudio-key";
    // The API reports usageMetadata as a running total, so the last event carries the whole call.
    String first =
        withUsage(responseDocument(RawPcmDecoderTest.raw(new short[] {1, 2}), null), 400, 42);
    String last =
        withUsage(responseDocument(RawPcmDecoderTest.raw(new short[] {3, 4}), "STOP"), 1_700, 42);
    server.enqueue(
        new MockResponse()
            .setResponseCode(HTTP_OK)
            .setBody("data: " + first + "\n\ndata: " + last + "\n\n"));
    SpendTracker spend = new SpendTracker();
    GeminiAiStudioTtsBackend backend = backend(config);
    backend.setSpendTracker(spend);

    backend.synthesizeStreaming(req(), (samples, rate) -> {});

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals("the cumulative total is banked once, not summed", 1_700, recorded.audioTokens());
    assertEquals(42, recorded.speechPromptTokens());
    assertEquals(1, recorded.voicedLines());
  }

  @Test
  public void aResponseWithoutUsageMetadataStillCountsTheLineButReportsNoTokens() {
    TestConfig config = new TestConfig();
    config.key = "aistudio-key";
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_OK).setBody(audioResponse(new short[] {1, 2})));
    SpendTracker spend = new SpendTracker();
    GeminiAiStudioTtsBackend backend = backend(config);
    backend.setSpendTracker(spend);

    assertNotNull(backend.synthesize(req()));

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals("the line was still voiced", 1, recorded.voicedLines());
    assertEquals(
        "no meter reading means no tokens, never a guessed one", 0, recorded.audioTokens());
  }

  @Test
  public void theTranslationHopBanksItsOwnMeteredTokensSeparately() {
    TestConfig config = new TestConfig();
    config.key = "aistudio-key";
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    server.enqueue(
        new MockResponse()
            .setResponseCode(HTTP_OK)
            .setBody(withUsage(geminiTranslation("Bonjour"), 75, 90)));
    server.enqueue(
        new MockResponse()
            .setResponseCode(HTTP_OK)
            .setBody(audioResponseWithUsage(new short[] {1, 2}, 1_700, 42)));
    SpendTracker spend = new SpendTracker();
    GeminiAiStudioTtsBackend backend = backend(config);
    backend.setSpendTracker(spend);

    assertNotNull(backend.synthesize(req()));

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals("the hop's input is measured", 90, recorded.translationInputTokens());
    assertEquals("as is its output", 75, recorded.translationOutputTokens());
    assertEquals(
        "the hop's text output must never land in the audio bucket, which bills 25x higher",
        1_700,
        recorded.audioTokens());
    assertEquals("the speech call's own prompt stays its own", 42, recorded.speechPromptTokens());
  }
}
