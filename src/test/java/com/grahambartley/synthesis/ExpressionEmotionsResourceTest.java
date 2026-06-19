package com.grahambartley.synthesis;

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
 * Pins the shape of the bundled {@code expression-emotions.json} seed (#25). Detection (#26) and
 * the backends depend on this resource parsing cleanly: every non-documentation key must be an
 * integer animation id and every value must name a valid {@link Emotion}. Documentation keys (those
 * starting with {@code _}, e.g. the {@code _meta} unverified-seed note) are allowed and skipped.
 */
public class ExpressionEmotionsResourceTest {

  private static final String RESOURCE = "/expression-emotions.json";

  @Test
  public void resourceParsesWithIntegerKeysAndValidEmotionValues() throws Exception {
    JsonObject root = load();

    boolean sawAtLeastOneId = false;
    for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith("_")) {
        // Documentation key (e.g. _meta); not an id.
        continue;
      }
      // Key must parse as an integer animation id.
      Integer.parseInt(key);
      // Value must be a valid Emotion enum constant.
      Emotion.valueOf(entry.getValue().getAsString());
      sawAtLeastOneId = true;
    }

    assertTrue("seed must contain at least one animationId -> Emotion entry", sawAtLeastOneId);
  }

  @Test
  public void resourceCarriesTheUnverifiedSeedNote() throws Exception {
    JsonObject root = load();
    assertTrue("seed must carry a _meta documentation key", root.has("_meta"));
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
