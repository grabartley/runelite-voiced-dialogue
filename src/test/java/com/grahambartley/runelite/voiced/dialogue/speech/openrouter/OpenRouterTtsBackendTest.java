package com.grahambartley.runelite.voiced.dialogue.speech.openrouter;

import static com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp.HTTP_TOO_MANY_REQUESTS;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.FAST_RETRY;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.body;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.bodyForEmotion;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.completeAudio;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.concat;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.enqueueChat;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.enqueuePcm;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.keyedConfig;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.line;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.req;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.sentBody;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.shutDownQuietly;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.startedServer;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.style;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.truncatedAudio;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterTtsBackend.WARM_UP_CONNECTIONS;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PAYMENT_REQUIRED;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.RawPcmDecoder;
import com.grahambartley.runelite.voiced.dialogue.audio.TestPcm;
import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
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
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OpenRouterTtsBackendTest {

  private MockWebServer server;
  private OkHttpClient client;
  private int notices;
  private final SpendTracker spend = new SpendTracker();

  @Before
  public void setUp() throws Exception {
    server = startedServer();
    client = new OkHttpClient();
  }

  @After
  public void tearDown() {
    shutDownQuietly(server);
  }

  private OpenRouterTtsBackend backend(VoicedDialogueConfig config) {
    return OpenRouterRequests.backend(client, server, config);
  }

  private OpenRouterTtsBackend backend(VoicedDialogueConfig config, RetryTuning tuning) {
    return OpenRouterRequests.backend(client, server, config, tuning);
  }

  private OpenRouterTtsBackend noticedBackend(OpenRouterTtsBackend backend) {
    backend.setNotice(msg -> notices++);
    return backend;
  }

  private OpenRouterTtsBackend costedBackend(VoicedDialogueConfig config) {
    OpenRouterTtsBackend backend = backend(config);
    backend.setSpendTracker(spend);
    return backend;
  }

  @Test
  public void availabilityRequiresKey() {
    MutableTestConfig config = new MutableTestConfig();
    assertFalse("blank key -> unavailable", backend(config).isAvailable());

    config.openRouterKey = "   ";
    assertFalse("whitespace-only key -> unavailable", backend(config).isAvailable());

    config.openRouterKey = "sk-or-abc";
    assertTrue("a key set -> available", backend(config).isAvailable());
  }

  @Test
  public void advertisesTheFullGeminiEmotionSet() {
    assertEquals(
        "every chat-head emotion is renderable, so none is downgraded away",
        EnumSet.of(Emotion.NEUTRAL, Emotion.HAPPY, Emotion.SAD, Emotion.ANGRY, Emotion.SCARED),
        backend(new MutableTestConfig()).supportedEmotions());
  }

  @Test
  public void everyEmotionSendsOnlyTheSpokenLineAsInput() throws Exception {
    for (Emotion emotion : EnumSet.allOf(Emotion.class)) {
      assertEquals(
          "Hello & welcome",
          bodyForEmotion(backend(keyedConfig()), server, emotion).get("input").getAsString());
    }
  }

  @Test
  public void emotionRidesInTheSpeechStyle() throws Exception {
    assertEquals(
        TestFixtures.TROLL_STYLE + " Sounding fearful.",
        style(bodyForEmotion(backend(keyedConfig()), server, Emotion.SCARED)));
    assertEquals(
        TestFixtures.TROLL_STYLE,
        style(bodyForEmotion(backend(keyedConfig()), server, Emotion.NEUTRAL)));
  }

  @Test
  public void aBlankProfileStillSendsTheLanguageDirection() throws Exception {
    enqueuePcm(server, (short) 1);
    CharacterProfile blank = new CharacterProfile(null, null, null, null);
    VoiceSpec voice = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE);

    backend(keyedConfig())
        .synthesize(new SynthesisRequest("Hi", voice, Emotion.NEUTRAL, blank, false, false));

    assertEquals("Speaking English. A man's voice.", style(sentBody(server)));
  }

  @Test
  public void nonDefaultSpeedUsesTheSpeedFieldAndLeavesTheStyleAlone() throws Exception {
    MutableTestConfig config = keyedConfig();
    config.speedPercent = 150;
    enqueuePcm(server, (short) 1);

    backend(config).synthesize(req());

    JsonObject body = sentBody(server);
    assertEquals(1.5, body.get("speed").getAsDouble(), 1e-9);
    assertEquals(TestFixtures.TROLL_STYLE, style(body));
  }

  @Test
  public void successfulResponseDecodesRawPcmAt24k() {
    short[] samples = {0, 16384, -16384, 32767};
    enqueuePcm(server, samples);

    Pcm pcm = backend(keyedConfig()).synthesize(req());

    assertNotNull("a 200 with raw PCM yields audio", pcm);
    assertEquals(24_000, pcm.getSampleRate());
    assertEquals(samples.length, pcm.getSamples().length);
  }

  @Test
  public void sendsBearerAuthAndJsonBody() throws Exception {
    MutableTestConfig config = keyedConfig();
    config.openRouterKey = "sk-or-secret";
    enqueuePcm(server, (short) 1);

    backend(config).synthesize(req());

    RecordedRequest recorded = server.takeRequest();
    assertEquals("POST", recorded.getMethod());
    assertEquals("/api/v1/audio/speech", recorded.getPath());
    assertEquals("Bearer sk-or-secret", recorded.getHeader("Authorization"));
    assertTrue(
        "a JSON content type is sent",
        recorded.getHeader("Content-Type").startsWith("application/json"));
    assertNotNull("a User-Agent is sent", recorded.getHeader("User-Agent"));
    assertEquals(
        "the OpenRouter app name is attributed",
        "RuneLite Voiced Dialogue",
        recorded.getHeader("X-Title"));

    JsonObject body = body(recorded);
    assertEquals("google/gemini-3.8-flash-tts", body.get("model").getAsString());
    assertEquals("Hello & welcome", body.get("input").getAsString());
    assertEquals("pcm", body.get("response_format").getAsString());
    assertEquals("Charon", body.get("voice").getAsString());
  }

  @Test
  public void voiceFieldComesFromTheGeminiVoiceMap() throws Exception {
    enqueuePcm(server, (short) 1);

    SynthesisRequest female =
        new SynthesisRequest(
            "Hi",
            VoiceSpec.npc(NpcRace.ELF, NpcGender.FEMALE),
            Emotion.NEUTRAL,
            TestFixtures.TROLL_PROFILE,
            false,
            false);
    OpenRouterTtsBackend backend = backend(keyedConfig());
    backend.synthesize(female);

    assertEquals(
        "the voice is whatever the map resolves for the spec",
        new GeminiVoiceMap().voiceFor(female.voice(), null),
        sentBody(server).get("voice").getAsString());
    assertNotEquals(
        "the backend keys each resolved voice apart",
        backend.cacheVariant(req()),
        backend.cacheVariant(female));
  }

  @Test
  public void aLongLineIsSentWholeAndKeyedTheSameAsAShortOne() throws Exception {
    MutableTestConfig config = keyedConfig();
    enqueuePcm(server, (short) 1);
    String longLine = "This is a long sentence. More text that must not be dropped by any cap.";
    SynthesisRequest request = line(longLine);
    OpenRouterTtsBackend backend = backend(config);

    backend.synthesize(request);

    assertEquals("the whole line is sent", longLine, sentBody(server).get("input").getAsString());
    SynthesisRequest shortLine = line("ab");
    assertEquals(
        "line length never enters the cache key, so no line is re-keyed by its length",
        backend.cacheVariant(shortLine),
        backend.cacheVariant(request));
  }

  @Test
  public void speedParamIsSentOnlyWhenNonDefault() throws Exception {
    MutableTestConfig config = keyedConfig();

    enqueuePcm(server, (short) 1);
    backend(config).synthesize(req());
    assertFalse("normal pace sends no speed param", sentBody(server).has("speed"));

    config.speedPercent = 150;
    enqueuePcm(server, (short) 1);
    backend(config).synthesize(req());
    assertEquals(
        "a non-default pace is sent as a fractional speed",
        1.5,
        sentBody(server).get("speed").getAsDouble(),
        0.0001);
  }

  @Test
  public void englishWithNoQuirkBypassesTheTranslationModel() throws Exception {
    enqueuePcm(server, (short) 1);

    assertNotNull(backend(keyedConfig()).synthesize(req()));
    assertEquals("English + no quirk makes exactly one (speech) call", 1, server.getRequestCount());
    assertTrue(
        "the only request is the speech call, never the translation model",
        server.takeRequest().getPath().endsWith("/audio/speech"));
  }

  @Test
  public void globalQuirkRoutesEnglishThroughTranslationAndKeepsTheBaseLanguageCode()
      throws Exception {
    MutableTestConfig config = keyedConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.ENGLISH;
    config.npcQuirk = VoicedDialogueConfig.SpeakingStyle.GEN_Z;
    enqueueChat(server, "no cap, well met");
    enqueuePcm(server, (short) 1);

    backend(config).synthesize(line("Well met."));

    RecordedRequest translation = server.takeRequest();
    assertTrue(
        "the quirk routes English through the translation hop",
        translation.getPath().endsWith("/chat/completions"));
    assertTrue(
        "the quirk is carried in the system prompt as a styled English target",
        translation.getBody().readUtf8().contains("Gen Z slang"));

    JsonObject speech = sentBody(server);
    assertEquals(
        "the rewritten line is what is voiced",
        "no cap, well met",
        speech.get("input").getAsString());
    assertEquals(
        "the language_code stays the base language, not the quirk",
        "en-GB",
        speech.get("language_code").getAsString());
  }

  @Test
  public void speakerClassPicksTheStyleForTranslation() throws Exception {
    MutableTestConfig config = keyedConfig();
    config.playerQuirk = VoicedDialogueConfig.SpeakingStyle.GEN_Z;
    config.npcQuirk = VoicedDialogueConfig.SpeakingStyle.NONE;

    enqueuePcm(server, (short) 1);
    backend(config).synthesize(line("Well met."));
    assertEquals("an NPC line with NPC style None skips translation", 1, server.getRequestCount());
    assertTrue(
        "the NPC line's only request is the speech call",
        server.takeRequest().getPath().endsWith("/audio/speech"));

    enqueueChat(server, "no cap, well met");
    enqueuePcm(server, (short) 1);
    backend(config).synthesize(line("Well met.", Emotion.NEUTRAL, false, true));
    RecordedRequest translation = server.takeRequest();
    assertTrue(
        "the player line routes through translation because the player style is set",
        translation.getPath().endsWith("/chat/completions"));
    assertTrue(
        "the player style is carried into the prompt",
        translation.getBody().readUtf8().contains("Gen Z slang"));
    assertTrue(
        "then the player line's speech call",
        server.takeRequest().getPath().endsWith("/audio/speech"));
  }

  @Test
  public void nonEnglishTargetTranslatesBeforeVoicingAndSetsLanguageCode() throws Exception {
    MutableTestConfig config = keyedConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    enqueueChat(server, "Bonjour");
    enqueuePcm(server, (short) 1);

    backend(config).synthesize(line("Hello"));

    RecordedRequest first = server.takeRequest();
    assertTrue("the translation hop runs first", first.getPath().endsWith("/chat/completions"));
    RecordedRequest second = server.takeRequest();
    assertTrue("then the speech call", second.getPath().endsWith("/audio/speech"));
    JsonObject body = body(second);
    assertEquals(
        "the spoken transcript is the translation, not the source",
        "Bonjour",
        body.get("input").getAsString());
    assertEquals(
        "the BCP-47 language_code matches the target",
        "fr-FR",
        body.get("language_code").getAsString());
  }

  @Test
  public void skipTranslationVoicesVerbatimUnderANonEnglishTarget() throws Exception {
    MutableTestConfig config = keyedConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    config.npcQuirk = VoicedDialogueConfig.SpeakingStyle.GEN_Z;
    enqueuePcm(server, (short) 1);

    backend(config).synthesize(line("Hello", Emotion.NEUTRAL, true, false));

    assertEquals(
        "skip-translation makes exactly one (speech) call, never the translation model",
        1,
        server.getRequestCount());
    RecordedRequest speech = server.takeRequest();
    assertTrue("the only request is the speech call", speech.getPath().endsWith("/audio/speech"));
    JsonObject body = body(speech);
    assertEquals(
        "the transcript is the source text exactly as typed, untranslated",
        "Hello",
        body.get("input").getAsString());
    assertFalse("an untranslated line carries no language_code", body.has("language_code"));
  }

  @Test
  public void normalLineStillTranslatesWhenSkipTranslationIsOff() throws Exception {
    MutableTestConfig config = keyedConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    enqueueChat(server, "Bonjour");
    enqueuePcm(server, (short) 1);

    backend(config).synthesize(line("Hello"));

    assertTrue(
        "a normal request still runs the translation hop first",
        server.takeRequest().getPath().endsWith("/chat/completions"));
    assertTrue("then the speech call", server.takeRequest().getPath().endsWith("/audio/speech"));
  }

  @Test
  public void translationFailureFailsTheLineWithoutCallingSpeech() {
    MutableTestConfig config = keyedConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_INTERNAL_ERROR).setBody("translation down"));

    OpenRouterTtsBackend backend = noticedBackend(backend(config));

    assertNull("a failed translation fails the line gracefully", backend.synthesize(req()));
    assertEquals("only the translation call was attempted", 1, server.getRequestCount());
    assertEquals("the failure surfaces one notice", 1, notices);
  }

  @Test
  public void rateLimitThrottlesThenClearsOnACleanCall() {
    OpenRouterTtsBackend backend = backend(keyedConfig());
    assertFalse("a fresh backend is not throttled", backend.isThrottled());

    server.enqueue(new MockResponse().setResponseCode(HTTP_TOO_MANY_REQUESTS).setBody("slow down"));
    backend.synthesize(req());
    assertTrue("a 429 opens a back-off window so prefetch holds off", backend.isThrottled());

    enqueuePcm(server, (short) 1);
    backend.synthesize(req());
    assertFalse("a clean call clears the back-off", backend.isThrottled());
  }

  @Test
  public void nonSuccessResponseReturnsNullWithOneNotice() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_UNAUTHORIZED).setBody("Unauthorized"));

    OpenRouterTtsBackend backend = noticedBackend(backend(keyedConfig()));

    Pcm pcm = backend.synthesize(req());

    assertNull("a non-2xx fails the line gracefully", pcm);
    assertEquals("the failure surfaces a one-time notice", 1, notices);
  }

  @Test
  public void outOfCreditsResponseSurfacesTopUpNotice() {
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_PAYMENT_REQUIRED).setBody("Insufficient credits"));

    String[] noticeText = {null};
    OpenRouterTtsBackend backend = backend(keyedConfig());
    backend.setNotice(msg -> noticeText[0] = msg);

    Pcm pcm = backend.synthesize(req());

    assertNull("a 402 fails the line gracefully", pcm);
    assertEquals(
        "a 402 surfaces the out-of-credits notice, not the key check",
        OpenRouterTtsBackend.OUT_OF_CREDITS_NOTICE,
        noticeText[0]);
  }

  @Test
  public void failureNoticeSplitsOutOfCreditsFromGenericFailures() {
    assertEquals(
        "402 gets the dedicated top-up notice",
        OpenRouterTtsBackend.OUT_OF_CREDITS_NOTICE,
        OpenRouterTtsBackend.failureNotice(HTTP_PAYMENT_REQUIRED));
    assertFalse(
        "the out-of-credits notice never blames the key",
        OpenRouterTtsBackend.OUT_OF_CREDITS_NOTICE.contains("key"));
  }

  @Test
  public void unauthorizedAndForbiddenBlameTheKey() {
    String unauthorized = OpenRouterTtsBackend.failureNotice(HTTP_UNAUTHORIZED);
    String forbidden = OpenRouterTtsBackend.failureNotice(HTTP_FORBIDDEN);

    assertTrue(unauthorized.contains("check your API key"));
    assertTrue(unauthorized.contains("HTTP 401"));
    assertTrue(forbidden.contains("check your API key"));
    assertTrue(forbidden.contains("HTTP 403"));
  }

  @Test
  public void otherStatusesAreARejectedRequestThatDoesNotBlameTheKey() {
    assertEquals(
        "OpenRouter rejected the TTS request (HTTP 400). This line was not voiced.",
        OpenRouterTtsBackend.failureNotice(HTTP_BAD_REQUEST));
    assertFalse(
        OpenRouterTtsBackend.failureNotice(HTTP_INTERNAL_ERROR).contains("check your API key"));
    assertFalse(
        OpenRouterTtsBackend.failureNotice(HTTP_TOO_MANY_REQUESTS).contains("check your API key"));
  }

  @Test
  public void transientEmptyBodyIsRetriedOnceAndRecovers() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(""));
    enqueuePcm(server, (short) 1, (short) 2, (short) 3);

    OpenRouterTtsBackend backend = noticedBackend(backend(keyedConfig()));

    assertNotNull("a single empty 200 is recovered by the retry", backend.synthesize(req()));
    assertEquals("the line was attempted twice", 2, server.getRequestCount());
    assertEquals("a recovered line surfaces no failure notice", 0, notices);
  }

  @Test
  public void repeatedEmptyBodyFailsAfterOneRetry() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(""));
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(""));

    OpenRouterTtsBackend backend = noticedBackend(backend(keyedConfig()));

    assertNull("two empty bodies in a row fail the line", backend.synthesize(req()));
    assertEquals("it retries exactly once, never storms", 2, server.getRequestCount());
    assertEquals("the persistent failure surfaces one notice", 1, notices);
  }

  @Test
  public void streamingFeedsChunksAndReturnsTheCompleteLineForCaching() {
    short[] samples = completeAudio();
    enqueuePcm(server, samples);

    List<float[]> fed = new ArrayList<>();
    Pcm result = backend(keyedConfig()).synthesizeStreaming(req(), (chunk, rate) -> fed.add(chunk));

    float[] whole = RawPcmDecoder.decode(TestPcm.raw(samples), 24_000).getSamples();
    assertNotNull("a complete streamed line is returned for caching", result);
    assertEquals("the model sample rate is carried", 24_000, result.getSampleRate());
    assertArrayEquals(
        "the returned line matches the whole-buffer decode", whole, result.getSamples(), 1e-6f);
    assertTrue("audio was handed to the sink as it streamed", fed.size() >= 1);
    assertArrayEquals("the streamed chunks reconstruct the whole line", whole, concat(fed), 1e-6f);
  }

  @Test
  public void streamingRetriesAnEmptyBodyThenReturnsNull() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK));
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK));

    List<float[]> fed = new ArrayList<>();
    Pcm result = backend(keyedConfig()).synthesizeStreaming(req(), (chunk, rate) -> fed.add(chunk));

    assertNull("an all-empty streamed line is not voiced", result);
    assertEquals(
        "an empty body is retried once, like the buffered path", 2, server.getRequestCount());
    assertTrue("nothing ever played", fed.isEmpty());
  }

  @Test
  public void streamingPlaysATruncatedLineOnceButDoesNotCacheOrRetryIt() {
    enqueuePcm(server, truncatedAudio());

    List<float[]> fed = new ArrayList<>();
    Pcm result = backend(keyedConfig()).synthesizeStreaming(req(), (chunk, rate) -> fed.add(chunk));

    assertNull("a truncated streamed line is not returned for caching", result);
    assertFalse("but it still played through the sink as it arrived", fed.isEmpty());
    assertEquals(
        "a streamed line is not retried once it has begun playing", 1, server.getRequestCount());
  }

  @Test
  public void streamingFailsANon2xxFastWithoutPlaying() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_INTERNAL_ERROR).setBody("boom"));

    OpenRouterTtsBackend backend = noticedBackend(backend(keyedConfig()));
    List<float[]> fed = new ArrayList<>();
    Pcm result = backend.synthesizeStreaming(req(), (chunk, rate) -> fed.add(chunk));

    assertNull("a non-2xx streamed line is not voiced", result);
    assertTrue("nothing played", fed.isEmpty());
    assertEquals("non-2xx fails fast with no retry", 1, server.getRequestCount());
    assertEquals("and surfaces one notice", 1, notices);
  }

  @Test
  public void streamingTreatsAnOddLengthBodyAsIncompleteAndDoesNotCacheIt() {
    byte[] even = TestPcm.raw(completeAudio());
    byte[] odd = Arrays.copyOf(even, even.length + 1);
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(new Buffer().write(odd)));

    List<float[]> fed = new ArrayList<>();
    Pcm result = backend(keyedConfig()).synthesizeStreaming(req(), (chunk, rate) -> fed.add(chunk));

    assertFalse("the whole samples still played as they streamed", fed.isEmpty());
    assertNull("a misaligned (odd-length) stream is not returned for caching", result);
    assertEquals("no retry once it has begun playing", 1, server.getRequestCount());
  }

  @Test
  public void truncatedAudioIsRetriedOnceAndRecovers() {
    enqueuePcm(server, truncatedAudio());
    enqueuePcm(server, completeAudio());

    OpenRouterTtsBackend backend = noticedBackend(backend(keyedConfig()));

    assertNotNull("a truncated line is recovered by the retry", backend.synthesize(req()));
    assertEquals("the line was attempted twice", 2, server.getRequestCount());
    assertEquals("a recovered line surfaces no failure notice", 0, notices);
  }

  @Test
  public void repeatedTruncatedAudioFailsRatherThanCachingAClippedLine() {
    enqueuePcm(server, truncatedAudio());
    enqueuePcm(server, truncatedAudio());

    OpenRouterTtsBackend backend = noticedBackend(backend(keyedConfig()));

    assertNull(
        "a persistently truncated line is never voiced or cached", backend.synthesize(req()));
    assertEquals("it retries exactly once, never storms", 2, server.getRequestCount());
    assertEquals("the persistent failure surfaces one notice", 1, notices);
  }

  @Test
  public void undecodableBodyReturnsNull() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(HTTP_OK)
            .setBody(new Buffer().write(new byte[] {1, 2, 3})));

    assertNull(
        "undecodable audio fails the line gracefully", backend(keyedConfig()).synthesize(req()));
  }

  @Test
  public void unavailableBackendDoesNotCallNetwork() {
    OpenRouterTtsBackend backend = backend(new MutableTestConfig());

    String[] last = {null};
    backend.setNotice(
        msg -> {
          notices++;
          last[0] = msg;
        });

    assertNull(backend.synthesize(req()));
    assertEquals("no HTTP request when unavailable", 0, server.getRequestCount());
    assertEquals("the missing-key notice fires", 1, notices);
    assertEquals(
        "it surfaces the shared no-key message", OpenRouterTtsBackend.NO_KEY_NOTICE, last[0]);
  }

  @Test
  public void missingKeyNoticeFiresOnEveryAttempt() {
    OpenRouterTtsBackend backend = noticedBackend(backend(new MutableTestConfig()));

    for (int i = 0; i < 3; i++) {
      assertNull("each no-key line fails gracefully", backend.synthesize(req()));
    }

    assertEquals("the no-key notice is not deduped: it fires on every attempt", 3, notices);
    assertEquals("still never hits the network", 0, server.getRequestCount());
  }

  @Test
  public void noticeFiresAtMostOnceAcrossRepeatedFailures() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_INTERNAL_ERROR));
    server.enqueue(new MockResponse().setResponseCode(HTTP_INTERNAL_ERROR));

    OpenRouterTtsBackend backend = noticedBackend(backend(keyedConfig()));

    backend.synthesize(req());
    backend.synthesize(req());

    assertEquals("repeated failures warn once", 1, notices);
  }

  @Test
  public void networkTimeoutIsRetriedOnceAndRecovers() {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
    enqueuePcm(server, (short) 1, (short) 2, (short) 3);

    OpenRouterTtsBackend backend = noticedBackend(backend(keyedConfig(), FAST_RETRY));

    assertNotNull(
        "a timed-out line is recovered by the backed-off retry", backend.synthesize(req()));
    assertEquals("the line was attempted twice", 2, server.getRequestCount());
    assertEquals("a recovered line surfaces no failure notice", 0, notices);
  }

  @Test
  public void repeatedNetworkTimeoutFailsGracefullyAfterOneRetry() {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

    OpenRouterTtsBackend backend = noticedBackend(backend(keyedConfig(), FAST_RETRY));

    assertNull("two timeouts in a row fail the line gracefully", backend.synthesize(req()));
    assertEquals("it retries exactly once, never storms", 2, server.getRequestCount());
    assertEquals("the persistent timeout surfaces one notice", 1, notices);
  }

  @Test
  public void unreachableHostFailsFastAndGracefully() throws Exception {
    OpenRouterTtsBackend backend = noticedBackend(backend(keyedConfig(), FAST_RETRY));
    server.shutdown();

    assertNull("an unreachable host fails the line gracefully", backend.synthesize(req()));
    assertEquals("the failure surfaces one notice", 1, notices);
  }

  @Test
  public void callBudgetGrowsWithTheLineLength() {
    OpenRouterTtsBackend backend = backend(new MutableTestConfig());

    Duration shortLine = backend.callBudgetFor(20);
    Duration longLine = backend.callBudgetFor(600);

    assertTrue(
        "a long line gets more time than a short one, because OpenRouter withholds audio until the"
            + " whole clip is generated",
        longLine.compareTo(shortLine) > 0);
    assertTrue(
        "a short line still fails fast rather than inheriting a long line's budget",
        shortLine.compareTo(Duration.ofSeconds(30)) < 0);
    assertTrue(
        "a 600 character line clears the ~23s it needs in practice",
        longLine.compareTo(Duration.ofSeconds(30)) > 0);
  }

  @Test
  public void callBudgetIsClampedToTheClientCeiling() {
    OpenRouterTtsBackend backend = backend(new MutableTestConfig(), FAST_RETRY);

    assertEquals(
        "an uncapped line cannot exceed the configured ceiling",
        FAST_RETRY.callTimeout,
        backend.callBudgetFor(100_000));
  }

  @Test
  public void callBudgetTreatsANegativeLengthAsEmpty() {
    OpenRouterTtsBackend backend = backend(new MutableTestConfig());

    assertEquals(backend.callBudgetFor(0), backend.callBudgetFor(-1));
  }

  private final List<RecordedRequest> warmUpRequests = new ArrayList<>();

  private OpenRouterTtsBackend warmedBackend(MutableTestConfig config) throws Exception {
    for (int i = 0; i < WARM_UP_CONNECTIONS; i++) {
      server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody("{}"));
    }
    OpenRouterTtsBackend backend = backend(config);
    backend.warmUp();
    warmUpRequests.clear();
    for (int i = 0; i < WARM_UP_CONNECTIONS; i++) {
      RecordedRequest received = server.takeRequest(10, TimeUnit.SECONDS);
      assertNotNull("a warm-up request never reached the server", received);
      warmUpRequests.add(received);
    }
    long deadline = System.currentTimeMillis() + 10_000;
    while (backend.idlePooledConnectionCount() < WARM_UP_CONNECTIONS
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
    return backend;
  }

  @Test
  public void warmUpOpensOneConnectionPerWarmUpSlotAgainstTheKeyEndpoint() throws Exception {
    OpenRouterTtsBackend backend = warmedBackend(keyedConfig());

    assertEquals(
        "warm-up opens a connection per slot",
        WARM_UP_CONNECTIONS,
        backend.pooledConnectionCount());
    for (RecordedRequest warmUp : warmUpRequests) {
      assertEquals(
          "warm-up hits the key endpoint, never the billable one", "/api/v1/key", warmUp.getPath());
      assertEquals("GET", warmUp.getMethod());
      assertEquals("Bearer sk-or-abc", warmUp.getHeader("Authorization"));
      assertEquals("each warm-up call opens its own connection", 0, warmUp.getSequenceNumber());
    }
  }

  @Test
  public void warmedConnectionIsReusedByTheNextSpokenLine() throws Exception {
    OpenRouterTtsBackend backend = warmedBackend(keyedConfig());
    enqueuePcm(server, (short) 0, (short) 16384, (short) -16384, (short) 0);

    assertNotNull("the warmed backend still synthesizes normally", backend.synthesize(req()));

    RecordedRequest speech = server.takeRequest();
    assertEquals("/api/v1/audio/speech", speech.getPath());
    assertTrue(
        "the spoken line reuses a warmed connection instead of handshaking",
        speech.getSequenceNumber() > 0);
  }

  @Test
  public void warmUpWithoutAnApiKeyIssuesNoRequest() {
    OpenRouterTtsBackend backend = backend(new MutableTestConfig());

    backend.warmUp();

    assertEquals("an unavailable backend is never warmed", 0, server.getRequestCount());
  }

  @Test
  public void warmUpIsANoOpWhileTheConnectionPoolIsAlreadyWarm() throws Exception {
    OpenRouterTtsBackend backend = warmedBackend(keyedConfig());

    backend.warmUp();

    assertEquals(
        "re-warming a warm pool spends nothing", WARM_UP_CONNECTIONS, server.getRequestCount());
  }

  @Test
  public void aVoicedLineCountsOnceAgainstTheCharactersActuallySent() throws Exception {
    enqueuePcm(server, (short) 1, (short) 2);
    OpenRouterTtsBackend backend = costedBackend(keyedConfig());

    assertNotNull(backend.synthesize(req()));

    JsonObject sent = sentBody(server);

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals(VoicedDialogueConfig.TtsProvider.OPENROUTER, recorded.provider());
    assertEquals(1, recorded.voicedLines());
    assertEquals(0, recorded.prefetchedLines());
    assertEquals(
        "the counted characters are the input the endpoint bills on",
        sent.get("input").getAsString().length(),
        recorded.speechCharacters());
  }

  @Test
  public void aPrefetchedLineCountsAsWarmingRatherThanAsAVoicedLine() {
    enqueuePcm(server, (short) 1, (short) 2);
    OpenRouterTtsBackend backend = costedBackend(keyedConfig());

    backend.synthesize(req().asPrefetch());

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals(0, recorded.voicedLines());
    assertEquals(1, recorded.prefetchedLines());
    assertTrue("warming still costs characters", recorded.speechCharacters() > 0);
  }

  @Test
  public void aStreamedLineCountsOnceWhenTheFirstAudioArrives() {
    enqueuePcm(server, (short) 1, (short) 2, (short) 3, (short) 4);
    OpenRouterTtsBackend backend = costedBackend(keyedConfig());

    backend.synthesizeStreaming(req(), (samples, rate) -> {});

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals("a streamed line is one billable line", 1, recorded.voicedLines());
  }

  @Test
  public void aFailedLineThatReturnsNoAudioCostsNothing() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_UNAUTHORIZED).setBody("bad key"));
    OpenRouterTtsBackend backend = costedBackend(keyedConfig());

    assertNull(backend.synthesize(req()));

    assertTrue("a rejected line never reaches the readout", spend.snapshot().isEmpty());
  }

  @Test
  public void aLineNeverSentForWantOfAKeyCostsNothing() {
    OpenRouterTtsBackend backend = costedBackend(new MutableTestConfig());

    assertNull(backend.synthesize(req()));

    assertTrue(spend.snapshot().isEmpty());
  }

  @Test
  public void theTranslationHopIsCountedInItsOwnBucket() {
    MutableTestConfig config = keyedConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    enqueueChat(server, "Bonjour");
    enqueuePcm(server, (short) 1, (short) 2);
    OpenRouterTtsBackend backend = costedBackend(config);

    assertNotNull(backend.synthesize(req()));

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals("one translation call", 1, recorded.translationCalls());
    assertEquals(
        "translation bills on the source line",
        "Hello & welcome".length(),
        recorded.translationCharacters());
    assertEquals("the spoken line is still counted once", 1, recorded.voicedLines());
  }
}
