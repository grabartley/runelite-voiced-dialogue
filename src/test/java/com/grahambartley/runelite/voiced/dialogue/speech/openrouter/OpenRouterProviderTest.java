package com.grahambartley.runelite.voiced.dialogue.speech.openrouter;

import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.bodyForEmotion;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.enqueuePcm;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.keyedConfig;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.req;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.sentBody;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.shutDownQuietly;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.startedServer;
import static com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterRequests.style;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import com.grahambartley.runelite.voiced.dialogue.speech.MutableTestConfig;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisRequest;
import com.grahambartley.runelite.voiced.dialogue.speech.TestFixtures;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OpenRouterProviderTest {

  private MockWebServer server;
  private OkHttpClient client;

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

  @Test
  public void alwaysPinsThroughputSort() {
    JsonObject body = new JsonObject();
    OpenRouterProvider.apply(body);

    JsonObject provider = body.getAsJsonObject("provider");
    assertEquals(
        "every call routes to the fastest provider (the :nitro equivalent)",
        "throughput",
        provider.get("sort").getAsString());
    assertFalse("plain routing carries no provider options", provider.has("options"));
  }

  @Test
  public void speechMetadataMergesIntoTheThroughputProviderBlock() {
    JsonObject metadata = new JsonObject();
    metadata.addProperty("style", "Calm.");
    JsonObject body = new JsonObject();
    OpenRouterProvider.apply(body, metadata);

    JsonObject provider = body.getAsJsonObject("provider");
    assertEquals("throughput", provider.get("sort").getAsString());
    assertEquals(
        "Calm.",
        provider
            .getAsJsonObject("options")
            .getAsJsonObject("google-ai-studio")
            .getAsJsonObject("speech_metadata")
            .get("style")
            .getAsString());
  }

  @Test
  public void emotionRidesInTheSpeechStyle() throws Exception {
    enqueuePcm(server, (short) 1);
    assertEquals(
        TestFixtures.TROLL_STYLE + " Sounding fearful.",
        style(bodyForEmotion(backend(keyedConfig()), server, Emotion.SCARED)));
    enqueuePcm(server, (short) 1);
    assertEquals(
        TestFixtures.TROLL_STYLE,
        style(bodyForEmotion(backend(keyedConfig()), server, Emotion.NEUTRAL)));
  }

  @Test
  public void profileAndEmotionTravelInProviderOptionsNotInTheInput() throws Exception {
    enqueuePcm(server, (short) 1);

    backend(keyedConfig())
        .synthesize(
            new SynthesisRequest(
                "You no take candle!",
                VoiceSpec.npc(NpcRace.TROLL, NpcGender.MALE),
                Emotion.ANGRY,
                TestFixtures.TROLL_PROFILE,
                false,
                false));

    JsonObject body = sentBody(server);
    assertEquals("You no take candle!", body.get("input").getAsString());
    assertEquals(TestFixtures.TROLL_STYLE + " Sounding angry.", style(body));
    assertEquals(
        "the speech options merge into the throughput routing block",
        "throughput",
        body.getAsJsonObject("provider").get("sort").getAsString());
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
  public void everyRequestRoutesForThroughput() throws Exception {
    enqueuePcm(server, (short) 1);

    backend(keyedConfig()).synthesize(req());

    assertEquals(
        "every TTS call asks for the fastest provider",
        "throughput",
        sentBody(server).getAsJsonObject("provider").get("sort").getAsString());
  }
}
