package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import com.grahambartley.runelite.voiced.dialogue.speech.MutableTestConfig;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisRequest;
import com.grahambartley.runelite.voiced.dialogue.speech.TestFixtures;
import okhttp3.mockwebserver.RecordedRequest;

final class AiStudioRequests {

  private AiStudioRequests() {}

  static MutableTestConfig keyedConfig() {
    MutableTestConfig config = new MutableTestConfig();
    config.googleAiStudioKey = "AIza-abc";
    return config;
  }

  static SynthesisRequest req() {
    return new SynthesisRequest(
        "Hello & welcome",
        VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE),
        Emotion.NEUTRAL,
        TestFixtures.TROLL_PROFILE,
        false,
        false);
  }

  static JsonObject body(RecordedRequest request) {
    return new JsonParser().parse(request.getBody().readUtf8()).getAsJsonObject();
  }

  static String text(JsonObject body) {
    return textPart(body).get("text").getAsString();
  }

  static String style(JsonObject body) {
    return textPart(body).getAsJsonObject("speech_metadata").get("style").getAsString();
  }

  static String responseMimeType(JsonObject body) {
    return body.getAsJsonObject("generationConfig")
        .getAsJsonObject("responseFormat")
        .getAsJsonObject("audio")
        .get("mimeType")
        .getAsString();
  }

  static boolean hasSpeechMetadata(JsonObject body) {
    return textPart(body).has("speech_metadata");
  }

  private static JsonObject textPart(JsonObject body) {
    return body.getAsJsonArray("contents")
        .get(0)
        .getAsJsonObject()
        .getAsJsonArray("parts")
        .get(0)
        .getAsJsonObject();
  }
}
