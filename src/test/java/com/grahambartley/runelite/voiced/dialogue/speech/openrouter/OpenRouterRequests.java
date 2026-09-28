package com.grahambartley.runelite.voiced.dialogue.speech.openrouter;

import static java.net.HttpURLConnection.HTTP_OK;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.TestPcm;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import com.grahambartley.runelite.voiced.dialogue.speech.MutableTestConfig;
import com.grahambartley.runelite.voiced.dialogue.speech.RetryTuning;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisRequest;
import com.grahambartley.runelite.voiced.dialogue.speech.TestFixtures;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;

final class OpenRouterRequests {

  static final RetryTuning FAST_RETRY =
      new RetryTuning(Duration.ofMillis(500), Duration.ofMillis(200), Duration.ofSeconds(1), 10, 0);

  private static final String SPEECH_PATH = "/api/v1/audio/speech";

  private OpenRouterRequests() {}

  static MockWebServer startedServer() throws IOException {
    MockWebServer server = new MockWebServer();
    server.start();
    return server;
  }

  static void shutDownQuietly(MockWebServer server) {
    try {
      server.shutdown();
    } catch (Exception ignored) {
    }
  }

  static OpenRouterTtsBackend backend(
      OkHttpClient client, MockWebServer server, VoicedDialogueConfig config) {
    return new OpenRouterTtsBackend(client, config, new Gson(), server.url(SPEECH_PATH).toString());
  }

  static OpenRouterTtsBackend backend(
      OkHttpClient client, MockWebServer server, VoicedDialogueConfig config, RetryTuning tuning) {
    return new OpenRouterTtsBackend(
        client, config, new Gson(), server.url(SPEECH_PATH).toString(), tuning);
  }

  static MutableTestConfig keyedConfig() {
    MutableTestConfig config = new MutableTestConfig();
    config.openRouterKey = "sk-or-abc";
    return config;
  }

  static SynthesisRequest req() {
    return line("Hello & welcome");
  }

  static SynthesisRequest line(String text) {
    return line(text, Emotion.NEUTRAL, false, false);
  }

  static SynthesisRequest line(
      String text, Emotion emotion, boolean skipTranslation, boolean player) {
    return new SynthesisRequest(
        text,
        VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE),
        emotion,
        TestFixtures.TROLL_PROFILE,
        skipTranslation,
        player);
  }

  static void enqueuePcm(MockWebServer server, short... samples) {
    server.enqueue(
        new MockResponse()
            .setResponseCode(HTTP_OK)
            .setBody(new Buffer().write(TestPcm.raw(samples))));
  }

  static void enqueueChat(MockWebServer server, String content) {
    server.enqueue(
        new MockResponse().setResponseCode(HTTP_OK).setBody(TestFixtures.chatResponse(content)));
  }

  static JsonObject body(RecordedRequest request) {
    return new JsonParser().parse(request.getBody().readUtf8()).getAsJsonObject();
  }

  static JsonObject sentBody(MockWebServer server) throws InterruptedException {
    return body(server.takeRequest());
  }

  static JsonObject bodyForEmotion(
      OpenRouterTtsBackend backend, MockWebServer server, Emotion emotion)
      throws InterruptedException {
    enqueuePcm(server, (short) 1);
    backend.synthesize(line("Hello & welcome", emotion, false, false));
    return sentBody(server);
  }

  static String style(JsonObject body) {
    return body.getAsJsonObject("provider")
        .getAsJsonObject("options")
        .getAsJsonObject("google-ai-studio")
        .getAsJsonObject("speech_metadata")
        .get("style")
        .getAsString();
  }

  static short[] truncatedAudio() {
    short[] s = new short[36_000];
    Arrays.fill(s, (short) 12_000);
    return s;
  }

  static short[] completeAudio() {
    short[] s = new short[40_800];
    Arrays.fill(s, 0, 36_000, (short) 12_000);
    return s;
  }

  static float[] concat(List<float[]> chunks) {
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
}
