package com.grahambartley.runelite.voiced.dialogue.speech;

import static com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp.HTTP_TOO_MANY_REQUESTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiTtsModel;
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

  private CloudSpeechExecutor executor() {
    MutableTestConfig config = new MutableTestConfig();
    CloudBackendSupport support =
        new CloudBackendSupport(
            config,
            VoicedDialogueConfig.TtsProvider.OPENROUTER,
            CloudSpeechExecutor.MAX_SPEECH_ATTEMPTS,
            RetryTuning.openRouter());
    return new CloudSpeechExecutor(
        config, support, new GeminiTtsModel(), "Test provider", "test-model", new StubOps());
  }

  private MockResponse rejection() {
    return new MockResponse().setResponseCode(HTTP_TOO_MANY_REQUESTS).setBody("{}");
  }

  private static SynthesisRequest request() {
    return new SynthesisRequest(
        "Hello", VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), Emotion.NEUTRAL);
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
    public CloudSpeechExecutor.PreparedSpeech buildRequests(
        CloudSpeechExecutor.SpokenLine line, SynthesisRequest request) {
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
