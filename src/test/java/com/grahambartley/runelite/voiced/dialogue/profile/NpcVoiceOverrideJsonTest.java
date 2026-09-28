package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonParser;
import org.junit.Test;

public class NpcVoiceOverrideJsonTest {

  private static NpcVoiceOverride parse(String json) {
    return NpcVoiceOverrideJson.parse(new JsonParser().parse(json));
  }

  @Test
  public void everyFieldSurvivesARoundTrip() {
    NpcVoiceOverride override =
        new NpcVoiceOverride("Guard", "Cockney", "Bored", "Slow", VoiceType.TYPE_B);

    assertEquals(override, NpcVoiceOverrideJson.parse(NpcVoiceOverrideJson.toJson(override)));
  }

  @Test
  public void onlySetFieldsAreWritten() {
    NpcVoiceOverride override =
        new NpcVoiceOverride(null, "Strong Scottish accent", null, null, VoiceType.TYPE_B);

    assertEquals(
        "{\"accent\":\"Strong Scottish accent\",\"voiceType\":\"TYPE_B\"}",
        NpcVoiceOverrideJson.toJson(override).toString());
  }

  @Test
  public void missingAndNullFieldsReadAsUnset() {
    assertEquals(
        new NpcVoiceOverride(null, null, "Warm", null, null),
        parse("{\"style\":\"Warm\",\"pace\":null}"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void anUnknownVoiceTypeIsRejected() {
    parse("{\"voiceType\":\"TYPE_C\"}");
  }

  @Test(expected = IllegalStateException.class)
  public void aNonObjectIsRejected() {
    parse("[\"an\",\"array\"]");
  }
}
