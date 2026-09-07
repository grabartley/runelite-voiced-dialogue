package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.RawPcmDecoder;
import com.grahambartley.runelite.voiced.dialogue.audio.TestPcm;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import com.grahambartley.runelite.voiced.dialogue.speech.MutableTestConfig;
import com.grahambartley.runelite.voiced.dialogue.speech.RetryTuning;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisRequest;
import com.grahambartley.runelite.voiced.dialogue.speech.TestFixtures;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiVoiceMap;
import com.grahambartley.runelite.voiced.dialogue.speech.spend.SpendTracker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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
public class AiStudioTtsBackendTest {

  private MockWebServer server;
  private OkHttpClient client;
  private final Gson gson = new Gson();
  private final SpendTracker spend = new SpendTracker();
  private final List<String> notices = new ArrayList<>();

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

  private static MutableTestConfig keyedConfig() {
    MutableTestConfig config = new MutableTestConfig();
    config.googleAiStudioKey = "AIza-abc";
    return config;
  }

  private AiStudioTtsBackend backend(MutableTestConfig config) {
    // Point speech and translation at the mock server while keeping the real header, JSON body,
    // SSE decode, and error logic; millisecond retry budgets so retry paths run without real
    // waits.
    return new AiStudioTtsBackend(
        client,
        config,
        gson,
        server.url("/v1beta/models/" + AiStudioTtsBackend.MODEL + ":generateContent").toString(),
        server.url("/v1beta/models/" + AiStudioTranslator.MODEL + ":generateContent").toString(),
        new RetryTuning(
            Duration.ofMillis(500), Duration.ofMillis(500), Duration.ofSeconds(1), 10, 0));
  }

  /** A keyed backend whose one-time notices land in {@link #notices}. */
  private AiStudioTtsBackend noticedBackend() {
    AiStudioTtsBackend backend = backend(keyedConfig());
    backend.setNotice(notices::add);
    return backend;
  }

  /** A backend whose billable calls land in {@link #spend}. */
  private AiStudioTtsBackend costedBackend(MutableTestConfig config) {
    AiStudioTtsBackend backend = backend(config);
    backend.setSpendTracker(spend);
    return backend;
  }

  private static SynthesisRequest req() {
    return new SynthesisRequest(
        "Hello & welcome", VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), Emotion.NEUTRAL);
  }

  /** The {@code contents[0].parts[0].text} a request or response document carries. */
  private static String spokenText(JsonObject body) {
    return body.getAsJsonArray("contents")
        .get(0)
        .getAsJsonObject()
        .getAsJsonArray("parts")
        .get(0)
        .getAsJsonObject()
        .get("text")
        .getAsString();
  }

  @Test
  public void availabilityRequiresKey() {
    MutableTestConfig config = new MutableTestConfig();
    assertFalse("blank key -> unavailable", backend(config).isAvailable());

    config.googleAiStudioKey = "   ";
    assertFalse("whitespace-only key -> unavailable", backend(config).isAvailable());

    config.googleAiStudioKey = "AIza-abc";
    assertTrue("a key set -> available", backend(config).isAvailable());
  }

  @Test
  public void missingKeyNoticeNamesTheProvider() {
    assertEquals(
        AiStudioTtsBackend.NO_KEY_NOTICE, backend(new MutableTestConfig()).missingKeyNotice());
    assertTrue(AiStudioTtsBackend.NO_KEY_NOTICE.contains("Google AI Studio"));
  }

  @Test
  public void missingKeyNoticeNamesTheDailyCapAndTheUncappedProvider() {
    assertTrue(
        "the notice warns of the preview model's daily ceiling before a player commits to a key",
        AiStudioTtsBackend.NO_KEY_NOTICE.contains("starts at 100 new lines a day"));
    assertTrue(
        "and points at the provider without one",
        AiStudioTtsBackend.NO_KEY_NOTICE.contains("OpenRouter has no daily cap"));
  }

  @Test
  public void missingKeyFailsWithoutARequest() {
    Pcm pcm = backend(new MutableTestConfig()).synthesize(req());

    assertNull("no key -> line not voiced", pcm);
    assertEquals("no HTTP call is made without a key", 0, server.getRequestCount());
  }

  @Test
  public void sendsApiKeyHeaderAndGeminiBody() throws Exception {
    MutableTestConfig config = keyedConfig();
    config.googleAiStudioKey = "AIza-secret";
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.audio(new short[] {1})));

    backend(config).synthesize(req());

    RecordedRequest recorded = server.takeRequest();
    assertEquals("POST", recorded.getMethod());
    assertEquals(
        "/v1beta/models/" + AiStudioTtsBackend.MODEL + ":generateContent", recorded.getPath());
    assertEquals("AIza-secret", recorded.getHeader("x-goog-api-key"));
    assertTrue(
        "a JSON content type is sent",
        recorded.getHeader("Content-Type").startsWith("application/json"));
    assertNotNull("a User-Agent is sent", recorded.getHeader("User-Agent"));

    JsonObject body = new JsonParser().parse(recorded.getBody().readUtf8()).getAsJsonObject();
    assertEquals("Hello & welcome", spokenText(body));
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
    short[] samples = {0, 16384, -16384, 32767};
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.audio(samples)));

    Pcm pcm = backend(keyedConfig()).synthesize(req());

    assertNotNull("a 200 with base64 PCM yields audio", pcm);
    assertEquals(24_000, pcm.getSampleRate());
    assertEquals(samples.length, pcm.getSamples().length);
  }

  @Test
  public void emotionTagAndProfileBlockLeadTheSpokenText() throws Exception {
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.audio(new short[] {1})));

    backend(keyedConfig())
        .synthesize(
            new SynthesisRequest(
                "You no take candle!",
                VoiceSpec.npc(NpcRace.TROLL, NpcGender.MALE),
                Emotion.ANGRY,
                TestFixtures.TROLL_PROFILE,
                false,
                false));

    JsonObject body =
        new JsonParser().parse(server.takeRequest().getBody().readUtf8()).getAsJsonObject();
    String text = spokenText(body);
    assertTrue(
        "the profile block leads and the emotion-tagged transcript follows",
        text.endsWith("[angry] You no take candle!"));
    assertTrue(
        "the profile block is present",
        text.startsWith(TestFixtures.TROLL_PROFILE.renderPromptBlock()));
  }

  @Test
  public void nonDefaultPaceBecomesAPromptDirection() throws Exception {
    MutableTestConfig config = keyedConfig();
    config.speedPercent = 150;
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.audio(new short[] {1})));

    backend(config).synthesize(req());

    JsonObject body =
        new JsonParser().parse(server.takeRequest().getBody().readUtf8()).getAsJsonObject();
    assertTrue(
        "a non-default pace has no API parameter, so it is a prompt direction",
        spokenText(body).startsWith("SPEAKING PACE: 150% of normal."));
  }

  @Test
  public void non2xxFailsTheLineGracefully() {
    AiStudioTtsBackend backend = noticedBackend();
    server.enqueue(new MockResponse().setResponseCode(HTTP_INTERNAL_ERROR).setBody("boom"));

    assertNull(backend.synthesize(req()));
    assertEquals("one notice for the failure", 1, notices.size());
    assertTrue(notices.get(0).contains("HTTP 500"));
  }

  @Test
  public void rateLimitOpensTheThrottleWindowAndNamesTheQuota() {
    AiStudioTtsBackend backend = noticedBackend();
    server.enqueue(AiStudioResponses.tooManyRequests("quota"));

    assertNull(backend.synthesize(req()));
    assertTrue("a 429 opens the prefetch back-off window", backend.isThrottled());
    assertEquals(AiStudioQuotaFailure.QUOTA_NOTICE, notices.get(0));
  }

  @Test
  public void theQuotaNoticeIsWordedFromTheRejectionBody() {
    AiStudioTtsBackend backend = noticedBackend();
    server.enqueue(AiStudioResponses.quotaRejection());

    assertNull(backend.synthesize(req()));
    assertTrue(
        "the reported cap reaches the player, not a guess at one",
        notices.get(0).contains("daily request cap of 100"));
  }

  @Test
  public void theStreamedPathWordsTheQuotaNoticeFromItsRejectionToo() {
    // The path a live cache-missed line takes.
    AiStudioTtsBackend backend = noticedBackend();
    server.enqueue(AiStudioResponses.quotaRejection());

    assertNull(backend.synthesizeStreaming(req(), (samples, rate) -> {}));
    assertTrue("a 429 opens the prefetch back-off window", backend.isThrottled());
    assertEquals(
        "a refused line is not retried into the closed window", 1, server.getRequestCount());
    assertTrue(
        "the reported cap reaches the player, not a guess at one",
        notices.get(0).contains("daily request cap of 100"));
  }

  @Test
  public void aStatedWaitStopsTheBackendSendingAnythingElse() {
    AiStudioTtsBackend backend = backend(keyedConfig());
    server.enqueue(AiStudioResponses.quotaRejection());

    assertNull(backend.synthesize(req()));
    assertEquals(1, server.getRequestCount());

    // A spare rejection means a line that wrongly escapes fails the count rather than blocking on
    // an empty queue.
    server.enqueue(AiStudioResponses.quotaRejection());
    assertNull("a line inside the stated wait is not voiced", backend.synthesize(req()));
    assertNull(backend.synthesizeStreaming(req(), (samples, rate) -> {}));
    assertEquals(
        "and never reaches the provider, which said it would refuse it",
        1,
        server.getRequestCount());
  }

  @Test
  public void changedCredentialsClearTheStatedWaitSoTheNextLineIsSent() {
    AiStudioTtsBackend backend = backend(keyedConfig());
    server.enqueue(AiStudioResponses.quotaRejection());
    assertNull(backend.synthesize(req()));

    backend.clearRateLimit();
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.audio(new short[] {1, 2})));

    assertNotNull(
        "the change the notice asked for must not be punished", backend.synthesize(req()));
  }

  @Test
  public void aRejectionStatingNoWaitStillLetsTheNextLineTry() {
    AiStudioTtsBackend backend = backend(keyedConfig());
    server.enqueue(AiStudioResponses.tooManyRequests("quota"));
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.audio(new short[] {1, 2})));

    assertNull(backend.synthesize(req()));

    assertNotNull(
        "a guessed window must not silence a line that might succeed", backend.synthesize(req()));
    assertEquals(2, server.getRequestCount());
  }

  @Test
  public void emptyAudioIsRetriedOnceThenFails() {
    server.enqueue(AiStudioResponses.ok("{\"candidates\":[]}"));
    server.enqueue(AiStudioResponses.ok("{\"candidates\":[]}"));

    assertNull(backend(keyedConfig()).synthesize(req()));
    assertEquals("one retry for a transient empty response", 2, server.getRequestCount());
  }

  @Test
  public void emptyAudioRecoversOnTheRetry() {
    server.enqueue(AiStudioResponses.ok("{\"candidates\":[]}"));
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.audio(new short[] {1, 2})));

    Pcm pcm = backend(keyedConfig()).synthesize(req());

    assertNotNull("the transient empty response is recovered by the retry", pcm);
    assertEquals(2, server.getRequestCount());
  }

  @Test
  public void streamingFeedsTheSinkPerChunkAndReturnsTheWholeLineForCaching() throws Exception {
    short[] first = {1, 2, 3};
    short[] second = {4, 5};
    List<byte[]> chunks = new ArrayList<>();
    chunks.add(TestPcm.raw(first));
    chunks.add(TestPcm.raw(second));
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.sse(chunks, "STOP")));

    List<float[]> sunk = new ArrayList<>();
    Pcm pcm =
        backend(keyedConfig()).synthesizeStreaming(req(), (samples, rate) -> sunk.add(samples));

    assertEquals("each SSE audio part reaches the sink as its own chunk", 2, sunk.size());
    assertEquals(first.length, sunk.get(0).length);
    assertEquals(second.length, sunk.get(1).length);
    assertNotNull("a complete stream returns the whole line for caching", pcm);
    assertEquals(first.length + second.length, pcm.getSamples().length);

    RecordedRequest recorded = server.takeRequest();
    assertEquals(
        "the streaming path posts to the SSE streamGenerateContent endpoint",
        "/v1beta/models/" + AiStudioTtsBackend.MODEL + ":streamGenerateContent?alt=sse",
        recorded.getPath());
  }

  @Test
  public void streamingChunkSplitAcrossSamplesIsReassembled() {
    // One 16-bit sample split across two SSE events: an odd leading byte must be carried, never
    // dropped or played as a half sample.
    byte[] whole = TestPcm.raw(new short[] {1, 2, 3});
    byte[] head = new byte[3];
    byte[] tail = new byte[3];
    System.arraycopy(whole, 0, head, 0, 3);
    System.arraycopy(whole, 3, tail, 0, 3);
    List<byte[]> chunks = new ArrayList<>();
    chunks.add(head);
    chunks.add(tail);
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.sse(chunks, "STOP")));

    List<Float> sunk = new ArrayList<>();
    Pcm pcm =
        backend(keyedConfig())
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
    List<byte[]> chunks = new ArrayList<>();
    chunks.add(TestPcm.raw(new short[] {1, 2, 3}));
    // No STOP finish reason: the stream was cut before the model finished the line.
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.sse(chunks, null)));

    List<float[]> sunk = new ArrayList<>();
    Pcm pcm =
        backend(keyedConfig()).synthesizeStreaming(req(), (samples, rate) -> sunk.add(samples));

    assertEquals("what arrived still played", 1, sunk.size());
    assertNull("an incomplete stream is never handed back for caching", pcm);
  }

  @Test
  public void emptyStreamIsRetriedOnceThenFails() {
    server.enqueue(AiStudioResponses.ok(""));
    server.enqueue(AiStudioResponses.ok(""));

    List<float[]> sunk = new ArrayList<>();
    Pcm pcm =
        backend(keyedConfig()).synthesizeStreaming(req(), (samples, rate) -> sunk.add(samples));

    assertNull(pcm);
    assertTrue(sunk.isEmpty());
    assertEquals("one retry for a transient empty stream", 2, server.getRequestCount());
  }

  @Test
  public void cacheVariantFoldsInModelAndVoiceSoRendersNeverCollide() {
    AiStudioTtsBackend backend = backend(new MutableTestConfig());

    SynthesisRequest humanMale =
        new SynthesisRequest("a", VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), Emotion.NEUTRAL);
    SynthesisRequest elfFemale =
        new SynthesisRequest("a", VoiceSpec.npc(NpcRace.ELF, NpcGender.FEMALE), Emotion.NEUTRAL);

    String variant = backend.cacheVariant(humanMale);
    assertTrue(
        "the variant carries the AI Studio model id", variant.contains(AiStudioTtsBackend.MODEL));
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
    MutableTestConfig config = keyedConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.translation("Bonjour")));
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.audio(new short[] {1})));

    Pcm pcm = backend(config).synthesize(req());
    assertNotNull(pcm);

    RecordedRequest translation = server.takeRequest();
    assertEquals(
        "the first hop is the Flash Lite translation model",
        "/v1beta/models/" + AiStudioTranslator.MODEL + ":generateContent",
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
    assertEquals("the translated text is what gets voiced", "Bonjour", spokenText(speechBody));
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
    MutableTestConfig config = keyedConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    server.enqueue(new MockResponse().setResponseCode(HTTP_INTERNAL_ERROR).setBody("boom"));

    assertNull(backend(config).synthesize(req()));
    assertEquals("no speech call after a failed translation", 1, server.getRequestCount());
  }

  @Test
  public void languageFragmentReKeysTranslatedLinesInTheCacheVariant() {
    MutableTestConfig config = new MutableTestConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    AiStudioTtsBackend backend = backend(config);

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
    short[] samples = {100, -200, 300, -400, 500};
    List<byte[]> chunks = new ArrayList<>();
    chunks.add(TestPcm.raw(samples));
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.sse(chunks, "STOP")));

    Pcm streamed = backend(keyedConfig()).synthesizeStreaming(req(), (chunk, rate) -> {});

    assertNotNull(streamed);
    assertArrayEquals(
        "streamed decode is byte-identical to a whole-buffer decode",
        RawPcmDecoder.decode(TestPcm.raw(samples), 24_000).getSamples(),
        streamed.getSamples(),
        0f);
  }

  @Test
  public void aVoicedLineCountsOnceAgainstTheCharactersActuallySent() throws Exception {
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.audio(new short[] {1, 2})));
    AiStudioTtsBackend backend = costedBackend(keyedConfig());

    assertNotNull(backend.synthesize(req()));

    JsonObject sent =
        new JsonParser().parse(server.takeRequest().getBody().readUtf8()).getAsJsonObject();
    String input = spokenText(sent);

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
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.audio(new short[] {1, 2})));
    AiStudioTtsBackend backend = costedBackend(keyedConfig());

    backend.synthesize(req().asPrefetch());

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals(0, recorded.voicedLines());
    assertEquals(1, recorded.prefetchedLines());
    assertTrue("warming still costs characters", recorded.speechCharacters() > 0);
  }

  @Test
  public void aStreamedLineCountsOnceWhenTheFirstAudioArrives() {
    List<byte[]> chunks =
        Arrays.asList(TestPcm.raw(new short[] {1, 2}), TestPcm.raw(new short[] {3, 4}));
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.sse(chunks, "STOP")));
    AiStudioTtsBackend backend = costedBackend(keyedConfig());

    backend.synthesizeStreaming(req(), (samples, rate) -> {});

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals("a multi-chunk stream is still one billable line", 1, recorded.voicedLines());
  }

  @Test
  public void aFailedLineThatReturnsNoAudioCostsNothing() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_UNAUTHORIZED).setBody("bad key"));
    AiStudioTtsBackend backend = costedBackend(keyedConfig());

    assertNull(backend.synthesize(req()));

    assertTrue("a rejected line never reaches the readout", spend.snapshot().isEmpty());
  }

  @Test
  public void aLineNeverSentForWantOfAKeyCostsNothing() {
    AiStudioTtsBackend backend = costedBackend(new MutableTestConfig());

    assertNull(backend.synthesize(req()));

    assertTrue(spend.snapshot().isEmpty());
  }

  @Test
  public void theTranslationHopIsCountedInItsOwnBucket() {
    MutableTestConfig config = keyedConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.translation("Bonjour")));
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.audio(new short[] {1, 2})));
    AiStudioTtsBackend backend = costedBackend(config);

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
    server.enqueue(
        AiStudioResponses.ok(AiStudioResponses.audioWithUsage(new short[] {1, 2}, 1_700, 42)));
    AiStudioTtsBackend backend = costedBackend(keyedConfig());

    assertNotNull(backend.synthesize(req()));

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals("audio tokens are measured, not modelled", 1_700, recorded.audioTokens());
    assertEquals(42, recorded.speechPromptTokens());
  }

  @Test
  public void aStreamedLineBanksTheRunningTotalFromTheFinalEvent() {
    // The API reports usageMetadata as a running total, so the last event carries the whole call.
    String first =
        AiStudioResponses.withUsage(
            AiStudioResponses.audioDocument(TestPcm.raw(new short[] {1, 2}), null), 400, 42);
    String last =
        AiStudioResponses.withUsage(
            AiStudioResponses.audioDocument(TestPcm.raw(new short[] {3, 4}), "STOP"), 1_700, 42);
    server.enqueue(AiStudioResponses.ok("data: " + first + "\n\ndata: " + last + "\n\n"));
    AiStudioTtsBackend backend = costedBackend(keyedConfig());

    backend.synthesizeStreaming(req(), (samples, rate) -> {});

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals("the cumulative total is banked once, not summed", 1_700, recorded.audioTokens());
    assertEquals(42, recorded.speechPromptTokens());
    assertEquals(1, recorded.voicedLines());
  }

  @Test
  public void aResponseWithoutUsageMetadataStillCountsTheLineButReportsNoTokens() {
    server.enqueue(AiStudioResponses.ok(AiStudioResponses.audio(new short[] {1, 2})));
    AiStudioTtsBackend backend = costedBackend(keyedConfig());

    assertNotNull(backend.synthesize(req()));

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals("the line was still voiced", 1, recorded.voicedLines());
    assertEquals(
        "no meter reading means no tokens, never a guessed one", 0, recorded.audioTokens());
  }

  @Test
  public void theTranslationHopBanksItsOwnMeteredTokensSeparately() {
    MutableTestConfig config = keyedConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    server.enqueue(
        AiStudioResponses.ok(
            AiStudioResponses.withUsage(AiStudioResponses.translation("Bonjour"), 75, 90)));
    server.enqueue(
        AiStudioResponses.ok(AiStudioResponses.audioWithUsage(new short[] {1, 2}, 1_700, 42)));
    AiStudioTtsBackend backend = costedBackend(config);

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
