package com.grahambartley.runelite.voiced.dialogue.speech;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;

public final class TestFixtures {

  public static final CharacterProfile TROLL_PROFILE =
      new CharacterProfile(
          "Troll", "South London Brixton accent", "Deep, slow and booming", "Slow and heavy");

  public static final String TROLL_STYLE =
      "Audio profile: Troll, a character in a medieval fantasy world."
          + " Accent: South London Brixton accent. Style: Deep, slow and booming. Pace: Slow and heavy.";

  public static final CharacterProfile NARRATOR_PROFILE =
      new CharacterProfile(
          "Narrator",
          "Clear Received Pronunciation accent",
          "Calm and measured, reading aloud",
          "Unhurried, even pace");

  private TestFixtures() {}

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
