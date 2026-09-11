package com.grahambartley.runelite.voiced.dialogue.speech;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;

public final class TestFixtures {

  public static final CharacterProfile TROLL_PROFILE =
      new CharacterProfile(
          "Troll",
          "British English, South London Brixton accent.",
          "A huge, slow, simple-minded troll.",
          "Slow and heavy.");

  public static final CharacterProfile NARRATOR_PROFILE =
      new CharacterProfile(
          "Narrator",
          "Received Pronunciation British English.",
          "A storyteller reading a tale aloud.",
          "Normal.");

  private TestFixtures() {}

  public static String spokenTranscript(CharacterProfile profile, String payload) {
    String block = profile.renderPromptBlock();
    if (!payload.startsWith(block)) {
      throw new AssertionError("payload does not lead with the profile block: " + payload);
    }
    return payload.substring(block.length());
  }

  public static String chatResponse(String content) {
    JsonObject message = new JsonObject();
    message.addProperty("role", "assistant");
    message.addProperty("content", content);
    JsonObject choice = new JsonObject();
    choice.add("message", message);
    JsonArray choices = new JsonArray();
    choices.add(choice);
    JsonObject body = new JsonObject();
    body.add("choices", choices);
    return body.toString();
  }
}
