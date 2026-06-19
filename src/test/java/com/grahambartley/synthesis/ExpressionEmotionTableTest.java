package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.Test;

/**
 * Verifies the {@link ExpressionEmotionTable} loader and the documented default contract that #26's
 * resolver and the backends depend on: a mapped human-head id returns its seeded {@link Emotion},
 * while any unmapped id and {@code -1} resolve to {@link Emotion#NEUTRAL}.
 */
public class ExpressionEmotionTableTest {

  @Test
  public void mappedSeedIdsResolveToTheirEmotion() {
    ExpressionEmotionTable table = ExpressionEmotionTable.load();
    assertTrue("seed table must load at least one id", table.size() > 0);

    // Seed entries from expression-emotions.json (human/standard head).
    assertEquals(Emotion.NEUTRAL, table.resolve(9760));
    assertEquals(Emotion.SAD, table.resolve(9764));
    assertEquals(Emotion.SCARED, table.resolve(9780));
    assertEquals(Emotion.ANGRY, table.resolve(9788));
    assertEquals(Emotion.NEUTRAL, table.resolve(9808));
    assertEquals(Emotion.HAPPY, table.resolve(9851));
  }

  @Test
  public void unmappedIdResolvesToNeutral() {
    ExpressionEmotionTable table = ExpressionEmotionTable.load();
    // An id absent from the seed (e.g. a non-human head expression) is the default-NEUTRAL case.
    assertEquals(Emotion.NEUTRAL, table.resolve(123456));
  }

  @Test
  public void negativeOneResolvesToNeutral() {
    ExpressionEmotionTable table = ExpressionEmotionTable.load();
    // -1 means no/stale head animation (missing head, sprite dialogue, or the one-tick race).
    assertEquals(Emotion.NEUTRAL, table.resolve(-1));
  }

  @Test
  public void resolveNeverReturnsNull() {
    ExpressionEmotionTable table = ExpressionEmotionTable.load();
    assertNotNull(table.resolve(-1));
    assertNotNull(table.resolve(9760));
    assertNotNull(table.resolve(987654));
  }

  @Test
  public void parseSkipsDocKeysAndRejectsInvalidEmotion() {
    JsonObject good =
        new JsonParser().parse("{\"_meta\":\"note\",\"9760\":\"HAPPY\"}").getAsJsonObject();
    Map<Integer, Emotion> parsed = ExpressionEmotionTable.parse(good);
    assertEquals(1, parsed.size());
    assertEquals(Emotion.HAPPY, parsed.get(9760));

    JsonObject badEmotion = new JsonParser().parse("{\"9760\":\"FURIOUS\"}").getAsJsonObject();
    try {
      ExpressionEmotionTable.parse(badEmotion);
      org.junit.Assert.fail("expected an IllegalArgumentException for an invalid Emotion value");
    } catch (IllegalArgumentException expected) {
      // contract: invalid Emotion names fail loudly.
    }

    JsonObject badKey = new JsonParser().parse("{\"nine\":\"HAPPY\"}").getAsJsonObject();
    try {
      ExpressionEmotionTable.parse(badKey);
      org.junit.Assert.fail("expected an IllegalArgumentException for a non-integer id key");
    } catch (IllegalArgumentException expected) {
      // contract: non-integer keys fail loudly.
    }
  }
}
