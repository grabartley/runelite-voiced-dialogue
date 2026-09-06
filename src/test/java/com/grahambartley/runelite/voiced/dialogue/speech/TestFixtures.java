package com.grahambartley.runelite.voiced.dialogue.speech;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;

/** Response bodies and profiles shared across the synthesis tests. */
final class TestFixtures {

  /** A fully populated character profile with a distinctive accent to assert on. */
  static final CharacterProfile TROLL_PROFILE =
      new CharacterProfile(
          "Troll",
          "British English, South London Brixton accent.",
          "A huge, slow, simple-minded troll.",
          "Slow and heavy.");

  private TestFixtures() {}

  /** A chat-completions response whose single choice carries {@code content}. */
  static String chatResponse(String content) {
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
