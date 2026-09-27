package com.grahambartley.runelite.voiced.dialogue.speech.openrouter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.gson.JsonObject;
import org.junit.Test;

public class OpenRouterProviderTest {

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
}
