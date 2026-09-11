package com.grahambartley.runelite.voiced.dialogue.speech.openrouter;

import static com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp.HTTP_TOO_MANY_REQUESTS;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterTtsBackend.WARM_UP_CONNECTIONS;
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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisBackend;
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

/**
 * HTTP path, headers, JSON body, decode, availability gating, cache variant, and graceful failure.
 */
public class OpenRouterTtsBackendTest {

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
    // The connect-failure test shuts the server down itself; a second shutdown would throw.
    try {
      server.shutdown();
    } catch (Exception ignored) {
      // already shut down
    }
  }

  /**
   * Millisecond timeout + backoff so the network-timeout retry path runs without multi-second
   * waits.
   */
  private static final RetryTuning FAST_RETRY =
      new RetryTuning(Duration.ofMillis(500), Duration.ofMillis(200), Duration.ofSeconds(1), 10, 0);

  private static MutableTestConfig keyedConfig() {
    MutableTestConfig config = new MutableTestConfig();
    config.openRouterKey = "sk-or-abc";
    return config;
  }

  private OpenRouterTtsBackend backend(VoicedDialogueConfig config) {
    // Point the backend at the mock server while keeping the real header/body/decode/error logic.
    return new OpenRouterTtsBackend(
        client, config, gson, server.url("/api/v1/audio/speech").toString());
  }

  private OpenRouterTtsBackend backendWith(VoicedDialogueConfig config, RetryTuning tuning) {
    return new OpenRouterTtsBackend(
        client, config, gson, server.url("/api/v1/audio/speech").toString(), tuning);
  }

  private void enqueuePcm(short... samples) {
    server.enqueue(
        new MockResponse()
            .setResponseCode(HTTP_OK)
            .setBody(new Buffer().write(TestPcm.raw(samples))));
  }

  private void enqueueChat(String content) {
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_OK).setBody(TestFixtures.chatResponse(content)));
  }

  private static SynthesisRequest req() {
    return new SynthesisRequest(
        "Hello & welcome", VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), Emotion.NEUTRAL);
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
  public void prependsTheInlineStyleTagForEachEmotion() throws Exception {
    assertEquals("[happy] Hello & welcome", inputForEmotion(Emotion.HAPPY));
    assertEquals("[sad] Hello & welcome", inputForEmotion(Emotion.SAD));
    assertEquals("[angry] Hello & welcome", inputForEmotion(Emotion.ANGRY));
    assertEquals("[fearful] Hello & welcome", inputForEmotion(Emotion.SCARED));
  }

  @Test
  public void neutralEmotionSendsThePlainTextWithNoTag() throws Exception {
    assertEquals("Hello & welcome", inputForEmotion(Emotion.NEUTRAL));
  }

  /**
   * Synthesizes one line at the given emotion and returns the {@code input} field actually sent.
   */
  private String inputForEmotion(Emotion emotion) throws Exception {
    enqueuePcm((short) 1);

    SynthesisRequest request =
        new SynthesisRequest(
            "Hello & welcome", VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), emotion);
    backend(keyedConfig()).synthesize(request);

    return sentBody().get("input").getAsString();
  }

  /** The JSON body of the next request the mock server recorded. */
  private JsonObject sentBody() throws Exception {
    return new JsonParser().parse(server.takeRequest().getBody().readUtf8()).getAsJsonObject();
  }

  @Test
  public void successfulResponseDecodesRawPcmAt24k() {
    short[] samples = {0, 16384, -16384, 32767};
    enqueuePcm(samples);

    Pcm pcm = backend(keyedConfig()).synthesize(req());

    assertNotNull("a 200 with raw PCM yields audio", pcm);
    assertEquals(24_000, pcm.getSampleRate());
    assertEquals(samples.length, pcm.getSamples().length);
  }

  @Test
  public void sendsBearerAuthAndJsonBody() throws Exception {
    MutableTestConfig config = keyedConfig();
    config.openRouterKey = "sk-or-secret";
    enqueuePcm((short) 1);

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

    JsonObject body = new JsonParser().parse(recorded.getBody().readUtf8()).getAsJsonObject();
    assertEquals("google/gemini-3.1-flash-tts-preview", body.get("model").getAsString());
    assertEquals("Hello & welcome", body.get("input").getAsString());
    assertEquals("pcm", body.get("response_format").getAsString());
    assertEquals("Charon", body.get("voice").getAsString());
  }

  @Test
  public void voiceFieldComesFromTheGeminiVoiceMap() throws Exception {
    enqueuePcm((short) 1);

    SynthesisRequest female =
        new SynthesisRequest("Hi", VoiceSpec.npc(NpcRace.ELF, NpcGender.FEMALE), Emotion.NEUTRAL);
    backend(keyedConfig()).synthesize(female);

    assertEquals(
        "the voice is whatever the map resolves for the spec",
        new GeminiVoiceMap().voiceFor(female.voice()),
        sentBody().get("voice").getAsString());
  }

  @Test
  public void cacheVariantFoldsInModelAndVoiceSoRendersNeverCollide() {
    OpenRouterTtsBackend backend = backend(new MutableTestConfig());

    SynthesisRequest humanMale =
        new SynthesisRequest("a", VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), Emotion.NEUTRAL);
    SynthesisRequest elfFemale =
        new SynthesisRequest("a", VoiceSpec.npc(NpcRace.ELF, NpcGender.FEMALE), Emotion.NEUTRAL);

    String variant = backend.cacheVariant(humanMale);
    assertTrue(
        "the variant carries the fixed model id so no future model switch can replay its audio",
        variant.contains("google/gemini-3.1-flash-tts-preview"));
    assertTrue(
        "the variant carries the resolved Gemini voice",
        variant.contains(new GeminiVoiceMap().voiceFor(humanMale.voice())));
    assertNotEquals(
        "two specs that map to different voices never share a variant",
        backend.cacheVariant(humanMale),
        backend.cacheVariant(elfFemale));
  }

  @Test
  public void profilePrependsTheAudioProfileBlockBeforeTheEmotionTaggedTranscript()
      throws Exception {
    enqueuePcm((short) 1);

    backend(keyedConfig())
        .synthesize(
            new SynthesisRequest(
                "You no take candle!",
                VoiceSpec.npc(NpcRace.TROLL, NpcGender.MALE),
                Emotion.ANGRY,
                TestFixtures.TROLL_PROFILE,
                false,
                false));

    String input = sentBody().get("input").getAsString();
    assertTrue(
        "the static guard line leads the block",
        input.startsWith("VOICE ONLY THE TRANSCRIPT BELOW THE DIVIDER"));
    assertTrue("the AUDIO PROFILE block follows the guard", input.contains("AUDIO PROFILE: Troll"));
    assertTrue("the director's notes carry the accent", input.contains("Brixton"));
    assertTrue(
        "the emotion-tagged transcript follows the divider, so the two layers compose",
        input.contains("#### TRANSCRIPT\n[angry] You no take candle!"));
  }

  @Test
  public void cacheVariantFoldsInProfileSoDifferentProfilesNeverCollide() {
    OpenRouterTtsBackend backend = backend(new MutableTestConfig());
    VoiceSpec voice = VoiceSpec.npc(NpcRace.TROLL, NpcGender.MALE);
    SynthesisRequest noProfile = new SynthesisRequest("a", voice, Emotion.NEUTRAL);
    SynthesisRequest withProfile =
        new SynthesisRequest("a", voice, Emotion.NEUTRAL, TestFixtures.TROLL_PROFILE, false, false);
    SynthesisRequest otherProfile =
        new SynthesisRequest(
            "a",
            voice,
            Emotion.NEUTRAL,
            new CharacterProfile("Goblin", "East London.", "Mischievous.", "Quick."),
            false,
            false);

    assertFalse(
        "a line with no profile carries no profile fragment, so existing cache stays valid",
        backend.cacheVariant(noProfile).contains("|p"));
    assertEquals(
        "the profiled variant is exactly the unprofiled one plus the profile content key",
        backend.cacheVariant(noProfile) + "|p" + TestFixtures.TROLL_PROFILE.cacheKey(),
        backend.cacheVariant(withProfile));
    assertNotEquals(
        "a profiled line never shares a variant with the same unprofiled line",
        backend.cacheVariant(noProfile),
        backend.cacheVariant(withProfile));
    assertNotEquals(
        "two different profiles never share a variant",
        backend.cacheVariant(withProfile),
        backend.cacheVariant(otherProfile));
  }

  @Test
  public void cacheVariantChangesWithSpeedSoStaleAudioIsNeverServed() {
    MutableTestConfig config = new MutableTestConfig();
    OpenRouterTtsBackend backend = backend(config);
    SynthesisRequest line =
        new SynthesisRequest("a", VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), Emotion.NEUTRAL);

    String atDefaultPace = backend.cacheVariant(line);
    config.speedPercent = 150;
    assertNotEquals(
        "a non-default pace must re-key so cached normal-pace audio is not served",
        atDefaultPace,
        backend.cacheVariant(line));
  }

  @Test
  public void aLongLineIsSentWholeAndKeyedTheSameAsAShortOne() throws Exception {
    MutableTestConfig config = keyedConfig();
    enqueuePcm((short) 1);
    String longLine = "This is a long sentence. More text that must not be dropped by any cap.";
    SynthesisRequest request =
        new SynthesisRequest(
            longLine, VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), Emotion.NEUTRAL);
    OpenRouterTtsBackend backend = backend(config);

    backend.synthesize(request);

    assertEquals("the whole line is sent", longLine, sentBody().get("input").getAsString());
    SynthesisRequest shortLine =
        new SynthesisRequest("ab", VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), Emotion.NEUTRAL);
    assertEquals(
        "line length never enters the cache key, so no line is re-keyed by its length",
        backend.cacheVariant(shortLine),
        backend.cacheVariant(request));
  }

  @Test
  public void speedParamIsSentOnlyWhenNonDefault() throws Exception {
    MutableTestConfig config = keyedConfig();

    enqueuePcm((short) 1);
    backend(config).synthesize(req());
    assertFalse("normal pace sends no speed param", sentBody().has("speed"));

    config.speedPercent = 150;
    enqueuePcm((short) 1);
    backend(config).synthesize(req());
    assertEquals(
        "a non-default pace is sent as a fractional speed",
        1.5,
        sentBody().get("speed").getAsDouble(),
        0.0001);
  }

  @Test
  public void everyRequestRoutesForThroughput() throws Exception {
    enqueuePcm((short) 1);

    backend(keyedConfig()).synthesize(req());

    assertEquals(
        "every TTS call asks for the fastest provider",
        "throughput",
        sentBody().getAsJsonObject("provider").get("sort").getAsString());
  }

  @Test
  public void englishWithNoQuirkBypassesTheTranslationModel() throws Exception {
    // Default language English, default quirk None: the line must go straight to speech with no
    // translation hop, so a single enqueued speech response is enough.
    enqueuePcm((short) 1);

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
    // Even with English as the base, the NPC style forces the translation hop; it is served first.
    enqueueChat("no cap, well met");
    enqueuePcm((short) 1);

    backend(config)
        .synthesize(
            new SynthesisRequest(
                "Well met.", VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), Emotion.NEUTRAL));

    RecordedRequest translation = server.takeRequest();
    assertTrue(
        "the quirk routes English through the translation hop",
        translation.getPath().endsWith("/chat/completions"));
    assertTrue(
        "the quirk is carried in the system prompt as a styled English target",
        translation.getBody().readUtf8().contains("Gen Z slang"));

    JsonObject speech = sentBody();
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
  public void globalQuirkPartitionsTheCacheKey() {
    MutableTestConfig config = new MutableTestConfig();
    OpenRouterTtsBackend backend = backend(config);
    SynthesisRequest line =
        new SynthesisRequest("a", VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), Emotion.NEUTRAL);

    String plain = backend.cacheVariant(line);
    assertFalse("plain English with no style adds no language fragment", plain.contains("|l"));

    config.npcQuirk = VoicedDialogueConfig.SpeakingStyle.GEN_Z;
    assertNotEquals(
        "a style must not collide with the unstyled line", plain, backend.cacheVariant(line));
  }

  @Test
  public void speakerClassPicksTheStyleForTranslation() throws Exception {
    MutableTestConfig config = keyedConfig();
    config.playerQuirk = VoicedDialogueConfig.SpeakingStyle.GEN_Z;
    config.npcQuirk = VoicedDialogueConfig.SpeakingStyle.NONE;
    VoiceSpec voice = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE);

    // The NPC line: NPC style None -> straight to speech, a single call, no translation hop.
    enqueuePcm((short) 1);
    backend(config)
        .synthesize(
            new SynthesisRequest(
                "Well met.", voice, Emotion.NEUTRAL, null, false, /* player= */ false));
    assertEquals("an NPC line with NPC style None skips translation", 1, server.getRequestCount());
    assertTrue(
        "the NPC line's only request is the speech call",
        server.takeRequest().getPath().endsWith("/audio/speech"));

    // The player line: player style Gen Z -> translation hop first, then speech.
    enqueueChat("no cap, well met");
    enqueuePcm((short) 1);
    backend(config)
        .synthesize(
            new SynthesisRequest(
                "Well met.", voice, Emotion.NEUTRAL, null, false, /* player= */ true));
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
  public void perSpeakerClassStylePartitionsTheCacheKey() {
    MutableTestConfig config = new MutableTestConfig();
    config.playerQuirk = VoicedDialogueConfig.SpeakingStyle.GEN_Z;
    config.npcQuirk = VoicedDialogueConfig.SpeakingStyle.PIRATE;
    OpenRouterTtsBackend backend = backend(config);
    VoiceSpec voice = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE);
    SynthesisRequest playerLine =
        new SynthesisRequest("a", voice, Emotion.NEUTRAL, null, false, /* player= */ true);
    SynthesisRequest npcLine =
        new SynthesisRequest("a", voice, Emotion.NEUTRAL, null, false, /* player= */ false);

    assertNotEquals(
        "a player-styled and an NPC-styled line of the same text get distinct cache keys",
        backend.cacheVariant(playerLine),
        backend.cacheVariant(npcLine));
  }

  @Test
  public void styleOnOneClassLeavesTheOtherClassUntranslated() {
    MutableTestConfig config = new MutableTestConfig();
    config.playerQuirk = VoicedDialogueConfig.SpeakingStyle.NONE;
    config.npcQuirk = VoicedDialogueConfig.SpeakingStyle.GEN_Z;
    OpenRouterTtsBackend backend = backend(config);
    VoiceSpec voice = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE);
    SynthesisRequest playerLine =
        new SynthesisRequest("a", voice, Emotion.NEUTRAL, null, false, /* player= */ true);
    SynthesisRequest npcLine =
        new SynthesisRequest("a", voice, Emotion.NEUTRAL, null, false, /* player= */ false);

    assertFalse(
        "the player line, player style None, carries no language fragment so it skips translation",
        backend.cacheVariant(playerLine).contains("|l"));
    assertTrue(
        "the NPC line, NPC style Gen Z, folds the styled language into its key",
        backend.cacheVariant(npcLine).contains("|l"));
  }

  @Test
  public void nonEnglishTargetFoldsLanguageIntoTheCacheVariant() {
    MutableTestConfig config = new MutableTestConfig();
    OpenRouterTtsBackend backend = backend(config);
    SynthesisRequest line =
        new SynthesisRequest("a", VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), Emotion.NEUTRAL);

    String english = backend.cacheVariant(line);
    assertFalse("English (default) adds no language fragment", english.contains("|l"));

    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    String french = backend.cacheVariant(line);
    assertNotEquals(
        "the same line in another language must not share a cache key", english, french);
    assertTrue("the language is folded in", french.contains("|lfrench"));
  }

  @Test
  public void nonEnglishTargetTranslatesBeforeVoicingAndSetsLanguageCode() throws Exception {
    MutableTestConfig config = keyedConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    // The translator call is served first, then the speech call (same mock server, queue order).
    enqueueChat("Bonjour");
    enqueuePcm((short) 1);

    backend(config)
        .synthesize(
            new SynthesisRequest(
                "Hello", VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), Emotion.NEUTRAL));

    RecordedRequest first = server.takeRequest();
    assertTrue("the translation hop runs first", first.getPath().endsWith("/chat/completions"));
    RecordedRequest second = server.takeRequest();
    assertTrue("then the speech call", second.getPath().endsWith("/audio/speech"));
    JsonObject body = new JsonParser().parse(second.getBody().readUtf8()).getAsJsonObject();
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
    // A skip-translation line bypasses the hop entirely, so only the speech call is enqueued.
    enqueuePcm((short) 1);

    backend(config)
        .synthesize(
            new SynthesisRequest(
                "Hello",
                VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE),
                Emotion.NEUTRAL,
                null,
                true,
                false));

    assertEquals(
        "skip-translation makes exactly one (speech) call, never the translation model",
        1,
        server.getRequestCount());
    RecordedRequest speech = server.takeRequest();
    assertTrue("the only request is the speech call", speech.getPath().endsWith("/audio/speech"));
    JsonObject body = new JsonParser().parse(speech.getBody().readUtf8()).getAsJsonObject();
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
    enqueueChat("Bonjour");
    enqueuePcm((short) 1);

    backend(config)
        .synthesize(
            new SynthesisRequest(
                "Hello",
                VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE),
                Emotion.NEUTRAL,
                null,
                false,
                false));

    assertTrue(
        "a normal request still runs the translation hop first",
        server.takeRequest().getPath().endsWith("/chat/completions"));
    assertTrue("then the speech call", server.takeRequest().getPath().endsWith("/audio/speech"));
  }

  @Test
  public void skipTranslationOmitsTheLanguageFragmentFromTheCacheVariant() {
    MutableTestConfig config = new MutableTestConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    OpenRouterTtsBackend backend = backend(config);
    VoiceSpec voice = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE);

    SynthesisRequest dialogue =
        new SynthesisRequest("a", voice, Emotion.NEUTRAL, null, false, false);
    SynthesisRequest publicChat =
        new SynthesisRequest("a", voice, Emotion.NEUTRAL, null, true, false);

    assertTrue(
        "a translated dialogue line still folds the language in",
        backend.cacheVariant(dialogue).contains("|lfrench"));
    assertFalse(
        "a skip-translation line keeps the plain pre-translation key",
        backend.cacheVariant(publicChat).contains("|l"));
    assertNotEquals(
        "so an untranslated public-chat clip never collides with a translated dialogue line of the"
            + " same text",
        backend.cacheVariant(dialogue),
        backend.cacheVariant(publicChat));
  }

  @Test
  public void translationFailureFailsTheLineWithoutCallingSpeech() {
    MutableTestConfig config = keyedConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_INTERNAL_ERROR).setBody("translation down"));

    int[] notices = {0};
    OpenRouterTtsBackend backend = backend(config);
    backend.setNotice(msg -> notices[0]++);

    assertNull("a failed translation fails the line gracefully", backend.synthesize(req()));
    assertEquals("only the translation call was attempted", 1, server.getRequestCount());
    assertEquals("the failure surfaces one notice", 1, notices[0]);
  }

  @Test
  public void rateLimitThrottlesThenClearsOnACleanCall() {
    OpenRouterTtsBackend backend = backend(keyedConfig());
    assertFalse("a fresh backend is not throttled", backend.isThrottled());

    server.enqueue(new MockResponse().setResponseCode(HTTP_TOO_MANY_REQUESTS).setBody("slow down"));
    backend.synthesize(req());
    assertTrue("a 429 opens a back-off window so prefetch holds off", backend.isThrottled());

    enqueuePcm((short) 1);
    backend.synthesize(req());
    assertFalse("a clean call clears the back-off", backend.isThrottled());
  }

  @Test
  public void nonSuccessResponseReturnsNullWithOneNotice() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_UNAUTHORIZED).setBody("Unauthorized"));

    int[] notices = {0};
    OpenRouterTtsBackend backend = backend(keyedConfig());
    backend.setNotice(msg -> notices[0]++);

    Pcm pcm = backend.synthesize(req());

    assertNull("a non-2xx fails the line gracefully", pcm);
    assertEquals("the failure surfaces a one-time notice", 1, notices[0]);
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
    assertTrue(
        "other codes keep the generic key-check notice with the code for context",
        OpenRouterTtsBackend.failureNotice(HTTP_UNAUTHORIZED).contains("HTTP 401"));
    assertFalse(
        "the out-of-credits notice never blames the key",
        OpenRouterTtsBackend.OUT_OF_CREDITS_NOTICE.contains("key"));
  }

  @Test
  public void transientEmptyBodyIsRetriedOnceAndRecovers() {
    // First call comes back as an empty 200 (the transient glitch); the immediate retry succeeds.
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(""));
    enqueuePcm((short) 1, (short) 2, (short) 3);

    int[] notices = {0};
    OpenRouterTtsBackend backend = backend(keyedConfig());
    backend.setNotice(msg -> notices[0]++);

    assertNotNull("a single empty 200 is recovered by the retry", backend.synthesize(req()));
    assertEquals("the line was attempted twice", 2, server.getRequestCount());
    assertEquals("a recovered line surfaces no failure notice", 0, notices[0]);
  }

  @Test
  public void repeatedEmptyBodyFailsAfterOneRetry() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(""));
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK).setBody(""));

    int[] notices = {0};
    OpenRouterTtsBackend backend = backend(keyedConfig());
    backend.setNotice(msg -> notices[0]++);

    assertNull("two empty bodies in a row fail the line", backend.synthesize(req()));
    assertEquals("it retries exactly once, never storms", 2, server.getRequestCount());
    assertEquals("the persistent failure surfaces one notice", 1, notices[0]);
  }

  /** 1.5 s of full-amplitude audio with no trailing silence: a line cut off mid-utterance. */
  private static short[] truncatedAudio() {
    short[] s = new short[36_000];
    Arrays.fill(s, (short) 12_000);
    return s;
  }

  /** 1.5 s of audio that releases into 200 ms of silence: a complete line. */
  private static short[] completeAudio() {
    short[] s = new short[40_800];
    Arrays.fill(s, 0, 36_000, (short) 12_000);
    return s;
  }

  private static float[] concat(List<float[]> chunks) {
    int total = 0;
    for (float[] chunk : chunks) {
      total += chunk.length;
    }
    float[] out = new float[total];
    int pos = 0;
    for (float[] chunk : chunks) {
      System.arraycopy(chunk, 0, out, pos, chunk.length);
      pos += chunk.length;
    }
    return out;
  }

  @Test
  public void streamingFeedsChunksAndReturnsTheCompleteLineForCaching() {
    short[] samples = completeAudio();
    enqueuePcm(samples);

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
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK)); // empty body
    server.enqueue(new MockResponse().setResponseCode(HTTP_OK)); // empty again

    List<float[]> fed = new ArrayList<>();
    Pcm result = backend(keyedConfig()).synthesizeStreaming(req(), (chunk, rate) -> fed.add(chunk));

    assertNull("an all-empty streamed line is not voiced", result);
    assertEquals(
        "an empty body is retried once, like the buffered path", 2, server.getRequestCount());
    assertTrue("nothing ever played", fed.isEmpty());
  }

  @Test
  public void streamingPlaysATruncatedLineOnceButDoesNotCacheOrRetryIt() {
    // Full-amplitude with no trailing silence: heard as it streams, but not cacheable.
    enqueuePcm(truncatedAudio());

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

    int[] notices = {0};
    OpenRouterTtsBackend backend = backend(keyedConfig());
    backend.setNotice(msg -> notices[0]++);
    List<float[]> fed = new ArrayList<>();
    Pcm result = backend.synthesizeStreaming(req(), (chunk, rate) -> fed.add(chunk));

    assertNull("a non-2xx streamed line is not voiced", result);
    assertTrue("nothing played", fed.isEmpty());
    assertEquals("non-2xx fails fast with no retry", 1, server.getRequestCount());
    assertEquals("and surfaces one notice", 1, notices[0]);
  }

  @Test
  public void theDefaultStreamingImplementationFallsBackToBufferedAsOneChunk() {
    Pcm whole = new Pcm(new float[] {0.1f, -0.2f, 0.3f}, 24_000);
    SynthesisBackend buffered =
        new SynthesisBackend() {
          @Override
          public String id() {
            return "buffered-only";
          }

          @Override
          public boolean isAvailable() {
            return true;
          }

          @Override
          public EnumSet<Emotion> supportedEmotions() {
            return EnumSet.of(Emotion.NEUTRAL);
          }

          @Override
          public Pcm synthesize(SynthesisRequest request) {
            return whole;
          }
        };

    List<float[]> fed = new ArrayList<>();
    int[] rate = {0};
    Pcm result =
        buffered.synthesizeStreaming(
            req(),
            (chunk, r) -> {
              fed.add(chunk);
              rate[0] = r;
            });

    assertEquals("the buffered result is returned unchanged", whole, result);
    assertEquals("the whole line is delivered as a single chunk", 1, fed.size());
    assertArrayEquals(whole.getSamples(), fed.get(0), 0f);
    assertEquals("at the line's own sample rate", 24_000, rate[0]);
  }

  @Test
  public void streamingTreatsAnOddLengthBodyAsIncompleteAndDoesNotCacheIt() {
    // A complete-looking body plus one dangling byte: not a whole number of 16-bit samples, so the
    // decoder ends with a pending byte and the line must not be cached (played once, re-fetched).
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
    // The first line ends mid-utterance; the immediate retry returns a complete line.
    enqueuePcm(truncatedAudio());
    enqueuePcm(completeAudio());

    int[] notices = {0};
    OpenRouterTtsBackend backend = backend(keyedConfig());
    backend.setNotice(msg -> notices[0]++);

    assertNotNull("a truncated line is recovered by the retry", backend.synthesize(req()));
    assertEquals("the line was attempted twice", 2, server.getRequestCount());
    assertEquals("a recovered line surfaces no failure notice", 0, notices[0]);
  }

  @Test
  public void repeatedTruncatedAudioFailsRatherThanCachingAClippedLine() {
    enqueuePcm(truncatedAudio());
    enqueuePcm(truncatedAudio());

    int[] notices = {0};
    OpenRouterTtsBackend backend = backend(keyedConfig());
    backend.setNotice(msg -> notices[0]++);

    assertNull(
        "a persistently truncated line is never voiced or cached", backend.synthesize(req()));
    assertEquals("it retries exactly once, never storms", 2, server.getRequestCount());
    assertEquals("the persistent failure surfaces one notice", 1, notices[0]);
  }

  @Test
  public void undecodableBodyReturnsNull() {
    // A 200 whose body is an odd byte count is not whole 16-bit PCM, so it fails to decode.
    server.enqueue(
        new MockResponse()
            .setResponseCode(HTTP_OK)
            .setBody(new Buffer().write(new byte[] {1, 2, 3})));

    assertNull(
        "undecodable audio fails the line gracefully", backend(keyedConfig()).synthesize(req()));
  }

  @Test
  public void unavailableBackendDoesNotCallNetwork() {
    OpenRouterTtsBackend backend = backend(new MutableTestConfig()); // no key

    String[] last = {null};
    int[] notices = {0};
    backend.setNotice(
        msg -> {
          notices[0]++;
          last[0] = msg;
        });

    assertNull(backend.synthesize(req()));
    assertEquals("no HTTP request when unavailable", 0, server.getRequestCount());
    assertEquals("the missing-key notice fires", 1, notices[0]);
    assertEquals(
        "it surfaces the shared no-key message", OpenRouterTtsBackend.NO_KEY_NOTICE, last[0]);
  }

  @Test
  public void missingKeyNoticeFiresOnEveryAttempt() {
    OpenRouterTtsBackend backend = backend(new MutableTestConfig()); // no key

    int[] notices = {0};
    backend.setNotice(msg -> notices[0]++);

    for (int i = 0; i < 3; i++) {
      assertNull("each no-key line fails gracefully", backend.synthesize(req()));
    }

    assertEquals("the no-key notice is not deduped: it fires on every attempt", 3, notices[0]);
    assertEquals("still never hits the network", 0, server.getRequestCount());
  }

  @Test
  public void noticeFiresAtMostOnceAcrossRepeatedFailures() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_INTERNAL_ERROR));
    server.enqueue(new MockResponse().setResponseCode(HTTP_INTERNAL_ERROR));

    int[] notices = {0};
    OpenRouterTtsBackend backend = backend(keyedConfig());
    backend.setNotice(msg -> notices[0]++);

    backend.synthesize(req());
    backend.synthesize(req());

    assertEquals("repeated failures warn once", 1, notices[0]);
  }

  @Test
  public void networkTimeoutIsRetriedOnceAndRecovers() {
    // First attempt: the server accepts the connection but never replies, so the read times out.
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
    // The backed-off retry gets a clean line.
    enqueuePcm((short) 1, (short) 2, (short) 3);

    int[] notices = {0};
    OpenRouterTtsBackend backend = backendWith(keyedConfig(), FAST_RETRY);
    backend.setNotice(msg -> notices[0]++);

    assertNotNull(
        "a timed-out line is recovered by the backed-off retry", backend.synthesize(req()));
    assertEquals("the line was attempted twice", 2, server.getRequestCount());
    assertEquals("a recovered line surfaces no failure notice", 0, notices[0]);
  }

  @Test
  public void repeatedNetworkTimeoutFailsGracefullyAfterOneRetry() {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

    int[] notices = {0};
    OpenRouterTtsBackend backend = backendWith(keyedConfig(), FAST_RETRY);
    backend.setNotice(msg -> notices[0]++);

    assertNull("two timeouts in a row fail the line gracefully", backend.synthesize(req()));
    assertEquals("it retries exactly once, never storms", 2, server.getRequestCount());
    assertEquals("the persistent timeout surfaces one notice", 1, notices[0]);
  }

  @Test
  public void unreachableHostFailsFastAndGracefully() throws Exception {
    OpenRouterTtsBackend backend = backendWith(keyedConfig(), FAST_RETRY);
    // Shut the server down so the connection is refused outright (an offline-style failure).
    server.shutdown();

    int[] notices = {0};
    backend.setNotice(msg -> notices[0]++);

    assertNull("an unreachable host fails the line gracefully", backend.synthesize(req()));
    assertEquals("the failure surfaces one notice", 1, notices[0]);
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
    OpenRouterTtsBackend backend = backendWith(new MutableTestConfig(), FAST_RETRY);

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

  /** Warm-up requests the server received, in arrival order, captured by {@link #warmedBackend}. */
  private final List<RecordedRequest> warmUpRequests = new ArrayList<>();

  /**
   * Warms a backend and blocks until every warm-up request has been received and its connection is
   * free for the next line to reuse. Warm-up is asynchronous on both counts: a connection joins the
   * pool before the server finishes recording its request, and stays checked out until its response
   * body is drained, so waiting on either signal alone leaves a race.
   */
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
    enqueuePcm((short) 0, (short) 16384, (short) -16384, (short) 0);

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
    enqueuePcm((short) 1, (short) 2);
    SpendTracker spend = new SpendTracker();
    OpenRouterTtsBackend backend = backend(keyedConfig());
    backend.setSpendTracker(spend);

    assertNotNull(backend.synthesize(req()));

    JsonObject sent = sentBody();

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
    enqueuePcm((short) 1, (short) 2);
    SpendTracker spend = new SpendTracker();
    OpenRouterTtsBackend backend = backend(keyedConfig());
    backend.setSpendTracker(spend);

    backend.synthesize(req().asPrefetch());

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals(0, recorded.voicedLines());
    assertEquals(1, recorded.prefetchedLines());
    assertTrue("warming still costs characters", recorded.speechCharacters() > 0);
  }

  @Test
  public void aStreamedLineCountsOnceWhenTheFirstAudioArrives() {
    enqueuePcm((short) 1, (short) 2, (short) 3, (short) 4);
    SpendTracker spend = new SpendTracker();
    OpenRouterTtsBackend backend = backend(keyedConfig());
    backend.setSpendTracker(spend);

    backend.synthesizeStreaming(req(), (samples, rate) -> {});

    SpendTracker.ProviderSpend recorded = spend.snapshot().get(0);
    assertEquals("a streamed line is one billable line", 1, recorded.voicedLines());
  }

  @Test
  public void aFailedLineThatReturnsNoAudioCostsNothing() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_UNAUTHORIZED).setBody("bad key"));
    SpendTracker spend = new SpendTracker();
    OpenRouterTtsBackend backend = backend(keyedConfig());
    backend.setSpendTracker(spend);

    assertNull(backend.synthesize(req()));

    assertTrue("a rejected line never reaches the readout", spend.snapshot().isEmpty());
  }

  @Test
  public void aLineNeverSentForWantOfAKeyCostsNothing() {
    SpendTracker spend = new SpendTracker();
    OpenRouterTtsBackend backend = backend(new MutableTestConfig());
    backend.setSpendTracker(spend);

    assertNull(backend.synthesize(req()));

    assertTrue(spend.snapshot().isEmpty());
  }

  @Test
  public void theTranslationHopIsCountedInItsOwnBucket() {
    MutableTestConfig config = keyedConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    enqueueChat("Bonjour");
    enqueuePcm((short) 1, (short) 2);
    SpendTracker spend = new SpendTracker();
    OpenRouterTtsBackend backend = backend(config);
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
}
