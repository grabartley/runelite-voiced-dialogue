package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.Test;

public class ExpressionEmotionsResourceTest {

  private static final String RESOURCE = "/expression-emotions.json";

  @Test
  public void resourceParsesWithPositiveIntegerKeysAndValidNonNeutralEmotionValues()
      throws Exception {
    JsonObject root = load();

    boolean sawAtLeastOneId = false;
    for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith("_")) {
        continue;
      }
      int id = Integer.parseInt(key);
      assertTrue("seq id must be positive, was " + id, id > 0);
      Emotion emotion = Emotion.valueOf(entry.getValue().getAsString());
      assertFalse(
          "NEUTRAL is the default and must not be listed explicitly (id " + id + ")",
          emotion == Emotion.NEUTRAL);
      sawAtLeastOneId = true;
    }

    assertTrue("table must contain at least one seqId -> Emotion entry", sawAtLeastOneId);
  }

  @Test
  public void representativeIdsMapToExpectedEmotions() throws Exception {
    JsonObject root = load();
    assertEquals("HAPPY", root.get("567").getAsString());
    assertEquals("SCARED", root.get("596").getAsString());
    assertEquals("HAPPY", root.get("605").getAsString());
    assertEquals("SAD", root.get("610").getAsString());
    assertEquals("ANGRY", root.get("614").getAsString());
    assertEquals("ANGRY", root.get("8215").getAsString());
  }

  @Test
  public void resourceCarriesTheMetaNote() throws Exception {
    JsonObject root = load();
    assertTrue("table must carry a _meta documentation key", root.has("_meta"));
    String meta = root.get("_meta").getAsString();
    assertFalse("_meta note must not be empty", meta.isEmpty());
  }

  private JsonObject load() throws Exception {
    try (InputStream in = getClass().getResourceAsStream(RESOURCE)) {
      assertNotNull("expression-emotions.json must be bundled as a plugin resource", in);
      return new JsonParser()
          .parse(new InputStreamReader(in, StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }
}
