package com.grahambartley.synthesis;

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

/**
 * Pins the shape of the bundled {@code expression-emotions.json} table (#25). Detection (#26) and
 * the backends depend on this resource parsing cleanly: every non-documentation key must be an
 * integer animation id inside the documented expression range (9760-9862) and every value must name
 * a valid {@link Emotion}. Documentation keys (those starting with {@code _}, e.g. the {@code
 * _meta} note) are allowed and skipped.
 */
public class ExpressionEmotionsResourceTest {

  private static final String RESOURCE = "/expression-emotions.json";

  @Test
  public void resourceParsesWithIntegerKeysInRangeAndValidEmotionValues() throws Exception {
    JsonObject root = load();

    boolean sawAtLeastOneId = false;
    for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith("_")) {
        // Documentation key (e.g. _meta); not an id.
        continue;
      }
      // Key must parse as an integer animation id inside the documented expression enum range.
      int id = Integer.parseInt(key);
      assertTrue(
          "documented expression id must fall in 9760-9862, was " + id, id >= 9760 && id <= 9862);
      // Value must be a valid Emotion enum constant.
      Emotion.valueOf(entry.getValue().getAsString());
      sawAtLeastOneId = true;
    }

    assertTrue("table must contain at least one animationId -> Emotion entry", sawAtLeastOneId);
  }

  @Test
  public void representativeIdsMapToExpectedEmotions() throws Exception {
    JsonObject root = load();
    assertEquals("NEUTRAL", root.get("9760").getAsString());
    assertEquals("SAD", root.get("9764").getAsString());
    assertEquals("SCARED", root.get("9780").getAsString());
    assertEquals("ANGRY", root.get("9788").getAsString());
    assertEquals("HAPPY", root.get("9851").getAsString());
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
      // The bundled Gson predates the static JsonParser.parseReader API, so use the instance
      // method.
      return new JsonParser()
          .parse(new InputStreamReader(in, StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }
}
