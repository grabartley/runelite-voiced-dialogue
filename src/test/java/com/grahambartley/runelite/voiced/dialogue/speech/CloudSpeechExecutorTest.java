package com.grahambartley.runelite.voiced.dialogue.speech;

import static com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp.HTTP_TOO_MANY_REQUESTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiTtsModel;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiVoiceMap;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CloudSpeechExecutorTest {

  private MockWebServer server;
  private OkHttpClient client;

  private long bodyStatedWaitMillis;

  private boolean speedInStyle;

  private CloudSpeechExecutor.SpokenLine spoken;

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

  @Test
  public void aRetryAfterHeaderAloneClosesTheBackend() {
    CloudSpeechExecutor executor = executor();
    server.enqueue(rejection().setHeader("Retry-After", "60"));

    assertNull(executor.synthesize(request()));

    assertTrue(executor.isThrottled());
    server.enqueue(rejection());
    assertNull("the header alone states a wait", executor.synthesize(request()));
    assertEquals("nothing follows a wait the provider stated", 1, server.getRequestCount());
  }

  @Test
  public void aBodyHintAloneClosesTheBackend() {
    bodyStatedWaitMillis = 60_000;
    CloudSpeechExecutor executor = executor();
    server.enqueue(rejection());

    assertNull(executor.synthesize(request()));

    server.enqueue(rejection());
    assertNull(executor.synthesize(request()));
    assertEquals(1, server.getRequestCount());
  }

  @Test
  public void aRejectionStatingNothingLeavesTheNextLineFree() {
    CloudSpeechExecutor executor = executor();
    server.enqueue(rejection());
    server.enqueue(rejection());

    assertNull(executor.synthesize(request()));

    assertTrue("speculation still stands down", executor.isThrottled());
    assertNull(executor.synthesize(request()));
    assertEquals("a guessed window never silences a user line", 2, server.getRequestCount());
  }

  @Test
  public void aHeaderAndABodyHintAreTakenAtWhicheverReachesFurther() {
    bodyStatedWaitMillis = 1;
    CloudSpeechExecutor executor = executor();
    server.enqueue(rejection().setHeader("Retry-After", "60"));

    assertNull(executor.synthesize(request()));

    server.enqueue(rejection());
    assertNull("the header outreaches the body hint here", executor.synthesize(request()));
    assertEquals(1, server.getRequestCount());
  }

  @Test
  public void aStatedWaitReadOffTheStreamedPathClosesTheBackendToo() {
    bodyStatedWaitMillis = 60_000;
    CloudSpeechExecutor executor = executor();
    server.enqueue(rejection());

    assertNull(executor.synthesizeStreaming(request(), (samples, rate) -> {}));

    server.enqueue(rejection());
    assertNull(
        "a wait stated to the stream still closes the backend", executor.synthesize(request()));
    assertEquals(1, server.getRequestCount());
  }

  @Test
  public void aClearedRateLimitLetsTheBackendSendAgain() {
    CloudSpeechExecutor executor = executor();
    server.enqueue(rejection().setHeader("Retry-After", "60"));
    assertNull(executor.synthesize(request()));

    executor.clearRateLimit();

    assertFalse(executor.isThrottled());
    server.enqueue(rejection());
    assertNull(executor.synthesize(request()));
    assertEquals("the line reaches the provider again", 2, server.getRequestCount());
  }

  @Test
  public void theSpokenLineCarriesOnlyTheTextWhileTheProfileRidesInTheStyle() {
    CloudSpeechExecutor executor = executor();
    server.enqueue(rejection());

    executor.synthesize(request());

    assertEquals("Hello", spoken.input);
    assertEquals(TestFixtures.TROLL_STYLE, spoken.style);
  }

  @Test
  public void theStyleNamesTheSpokenLanguageFromTheSettings() {
    MutableTestConfig config = new MutableTestConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    server.enqueue(rejection());

    executor(config).synthesize(request());

    assertTrue(spoken.style.startsWith("Speaking French. A man's voice. Audio profile: Troll"));
  }

  @Test
  public void untranslatedTextIsStyledAsEnglishWhateverTheSpokenLanguage() {
    MutableTestConfig config = new MutableTestConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    server.enqueue(rejection());

    executor(config)
        .synthesize(
            new SynthesisRequest(
                "Hello",
                VoiceSpec.player(NpcGender.MALE),
                Emotion.NEUTRAL,
                TestFixtures.TROLL_PROFILE,
                true,
                true));

    assertEquals("Hello", spoken.input);
    assertTrue(spoken.style.startsWith("Speaking English. "));
  }

  @Test
  public void aProviderWithoutASpeedFieldGetsTheSpeedInTheStyle() {
    speedInStyle = true;
    MutableTestConfig config = new MutableTestConfig();
    config.speedPercent = 80;
    server.enqueue(rejection());

    executor(config).synthesize(request());

    assertEquals(TestFixtures.TROLL_STYLE + " Speaking at 80% of normal speed.", spoken.style);
    assertEquals(80, spoken.speedPercent);
  }

  @Test
  public void aProviderWithASpeedFieldKeepsTheSpeedOutOfTheStyle() {
    MutableTestConfig config = new MutableTestConfig();
    config.speedPercent = 80;
    server.enqueue(rejection());

    executor(config).synthesize(request());

    assertEquals(TestFixtures.TROLL_STYLE, spoken.style);
  }

  @Test
  public void theDefaultSpeedNeverAddsASpeedDirection() {
    speedInStyle = true;
    server.enqueue(rejection());

    executor().synthesize(request());

    assertEquals(TestFixtures.TROLL_STYLE, spoken.style);
  }

  @Test
  public void cacheVariantFoldsInVoiceButNotModelSoRendersNeverCollide() {
    CloudSpeechExecutor executor = executor();

    SynthesisRequest humanMale = humanMaleLine();
    SynthesisRequest elfFemale =
        new SynthesisRequest(
            "a",
            VoiceSpec.npc(NpcRace.ELF, NpcGender.FEMALE),
            Emotion.NEUTRAL,
            TestFixtures.TROLL_PROFILE,
            false,
            false);

    String variant = executor.cacheVariant(humanMale);
    assertFalse(
        "the variant leaves the model out so a model swap keeps every cached clip",
        variant.contains("gemini"));
    assertTrue(
        "the variant carries the resolved Gemini voice",
        variant.contains(new GeminiVoiceMap().voiceFor(humanMale.voice(), null)));
    assertNotEquals(
        "two specs that map to different voices never share a variant",
        executor.cacheVariant(humanMale),
        executor.cacheVariant(elfFemale));
  }

  @Test
  public void cacheVariantFoldsInProfileSoDifferentProfilesNeverCollide() {
    CloudSpeechExecutor executor = executor();
    VoiceSpec voice = VoiceSpec.npc(NpcRace.TROLL, NpcGender.MALE);
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

    assertTrue(
        "every line carries a profile, so every variant carries its fragment",
        executor.cacheVariant(withProfile).contains("|p"));
    assertTrue(
        "the fragment is the profile content key, which is what keeps cached audio addressable",
        executor.cacheVariant(withProfile).endsWith("|p" + TestFixtures.TROLL_PROFILE.cacheKey()));
    assertNotEquals(
        "two different profiles never share a variant",
        executor.cacheVariant(withProfile),
        executor.cacheVariant(otherProfile));
  }

  @Test
  public void cacheVariantChangesWithSpeedSoStaleAudioIsNeverServed() {
    MutableTestConfig config = new MutableTestConfig();
    CloudSpeechExecutor executor = executor(config);
    SynthesisRequest line = humanMaleLine();

    String atDefaultPace = executor.cacheVariant(line);
    config.speedPercent = 150;
    assertNotEquals(
        "a non-default pace must re-key so cached normal-pace audio is not served",
        atDefaultPace,
        executor.cacheVariant(line));
  }

  @Test
  public void globalQuirkPartitionsTheCacheKey() {
    MutableTestConfig config = new MutableTestConfig();
    CloudSpeechExecutor executor = executor(config);
    SynthesisRequest line = humanMaleLine();

    String plain = executor.cacheVariant(line);
    assertFalse("plain English with no style adds no language fragment", plain.contains("|l"));

    config.npcQuirk = VoicedDialogueConfig.SpeakingStyle.GEN_Z;
    assertNotEquals(
        "a style must not collide with the unstyled line", plain, executor.cacheVariant(line));
  }

  @Test
  public void perSpeakerClassStylePartitionsTheCacheKey() {
    MutableTestConfig config = new MutableTestConfig();
    config.playerQuirk = VoicedDialogueConfig.SpeakingStyle.GEN_Z;
    config.npcQuirk = VoicedDialogueConfig.SpeakingStyle.PIRATE;
    CloudSpeechExecutor executor = executor(config);
    VoiceSpec voice = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE);
    SynthesisRequest playerLine =
        new SynthesisRequest("a", voice, Emotion.NEUTRAL, TestFixtures.TROLL_PROFILE, false, true);
    SynthesisRequest npcLine =
        new SynthesisRequest("a", voice, Emotion.NEUTRAL, TestFixtures.TROLL_PROFILE, false, false);

    assertNotEquals(
        "a player-styled and an NPC-styled line of the same text get distinct cache keys",
        executor.cacheVariant(playerLine),
        executor.cacheVariant(npcLine));
  }

  @Test
  public void styleOnOneClassLeavesTheOtherClassUntranslated() {
    MutableTestConfig config = new MutableTestConfig();
    config.playerQuirk = VoicedDialogueConfig.SpeakingStyle.NONE;
    config.npcQuirk = VoicedDialogueConfig.SpeakingStyle.GEN_Z;
    CloudSpeechExecutor executor = executor(config);
    VoiceSpec voice = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE);
    SynthesisRequest playerLine =
        new SynthesisRequest("a", voice, Emotion.NEUTRAL, TestFixtures.TROLL_PROFILE, false, true);
    SynthesisRequest npcLine =
        new SynthesisRequest("a", voice, Emotion.NEUTRAL, TestFixtures.TROLL_PROFILE, false, false);

    assertFalse(
        "the player line, player style None, carries no language fragment so it skips translation",
        executor.cacheVariant(playerLine).contains("|l"));
    assertTrue(
        "the NPC line, NPC style Gen Z, folds the styled language into its key",
        executor.cacheVariant(npcLine).contains("|l"));
  }

  @Test
  public void nonEnglishTargetFoldsLanguageIntoTheCacheVariant() {
    MutableTestConfig config = new MutableTestConfig();
    CloudSpeechExecutor executor = executor(config);
    SynthesisRequest line = humanMaleLine();

    String english = executor.cacheVariant(line);
    assertFalse("English (default) adds no language fragment", english.contains("|l"));

    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    String french = executor.cacheVariant(line);
    assertNotEquals(
        "the same line in another language must not share a cache key", english, french);
    assertTrue("the language is folded in", french.contains("|lfrench"));
  }

  @Test
  public void skipTranslationOmitsTheLanguageFragmentFromTheCacheVariant() {
    MutableTestConfig config = new MutableTestConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    CloudSpeechExecutor executor = executor(config);
    VoiceSpec voice = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE);

    SynthesisRequest dialogue =
        new SynthesisRequest("a", voice, Emotion.NEUTRAL, TestFixtures.TROLL_PROFILE, false, false);
    SynthesisRequest publicChat =
        new SynthesisRequest("a", voice, Emotion.NEUTRAL, TestFixtures.TROLL_PROFILE, true, false);

    assertTrue(
        "a translated dialogue line still folds the language in",
        executor.cacheVariant(dialogue).contains("|lfrench"));
    assertFalse(
        "a skip-translation line keeps the plain pre-translation key",
        executor.cacheVariant(publicChat).contains("|l"));
    assertNotEquals(
        "so an untranslated public-chat clip never collides with a translated dialogue line of the"
            + " same text",
        executor.cacheVariant(dialogue),
        executor.cacheVariant(publicChat));
  }

  private CloudSpeechExecutor executor() {
    return executor(new MutableTestConfig());
  }

  private CloudSpeechExecutor executor(MutableTestConfig config) {
    CloudBackendSupport support =
        new CloudBackendSupport(
            config,
            VoicedDialogueConfig.TtsProvider.OPENROUTER,
            CloudSpeechExecutor.MAX_SPEECH_ATTEMPTS,
            RetryTuning.openRouter());
    return new CloudSpeechExecutor(
        config, support, new GeminiTtsModel(), "Test provider", new StubOps());
  }

  private MockResponse rejection() {
    return new MockResponse().setResponseCode(HTTP_TOO_MANY_REQUESTS).setBody("{}");
  }

  private static SynthesisRequest request() {
    return new SynthesisRequest(
        "Hello",
        VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE),
        Emotion.NEUTRAL,
        TestFixtures.TROLL_PROFILE,
        false,
        false);
  }

  private static SynthesisRequest humanMaleLine() {
    return new SynthesisRequest(
        "a",
        VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE),
        Emotion.NEUTRAL,
        TestFixtures.TROLL_PROFILE,
        false,
        false);
  }

  private final class StubOps implements CloudSpeechExecutor.Ops {

    @Override
    public String apiKey() {
      return "key";
    }

    @Override
    public String missingKeyNotice() {
      return "no key";
    }

    @Override
    public String translate(String text, String language, String apiKey) {
      return text;
    }

    @Override
    public boolean speedInStyle() {
      return speedInStyle;
    }

    @Override
    public CloudSpeechExecutor.PreparedSpeech buildRequests(
        CloudSpeechExecutor.SpokenLine line, SynthesisRequest request) {
      spoken = line;
      Request httpRequest =
          new Request.Builder()
              .url(server.url("/speech"))
              .post(RequestBody.create(CloudHttp.JSON_MEDIA_TYPE, "{}".getBytes()))
              .build();
      return new CloudSpeechExecutor.PreparedSpeech(
          httpRequest, line.speedRatio, line.input.length(), request.prefetch());
    }

    @Override
    public Call newCall(Request httpRequest, int inputLen) {
      return client.newCall(httpRequest);
    }

    @Override
    public CloudSpeechExecutor.DecodedSpeech decodeBuffered(
        byte[] bytes, CloudSpeechExecutor.PreparedSpeech prepared) {
      return CloudSpeechExecutor.DecodedSpeech.EMPTY;
    }

    @Override
    public CloudSpeechExecutor.StreamDrain newStreamDrain() {
      return (body, chunk) -> {};
    }

    @Override
    public String failureNotice(int httpCode, byte[] body) {
      return "failed";
    }

    @Override
    public long statedWaitMillis(byte[] body) {
      return bodyStatedWaitMillis;
    }

    @Override
    public String emptyBodyNotice() {
      return "empty";
    }
  }
}
