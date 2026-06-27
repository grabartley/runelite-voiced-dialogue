package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.Test;

/**
 * Verifies the {@link ExpressionEmotionTable} loader and the documented default contract that #26's
 * resolver and the backends depend on: a documented expression id returns its mapped {@link
 * Emotion}, while any unmapped id and {@code -1} resolve to {@link Emotion#NEUTRAL}.
 */
public class ExpressionEmotionTableTest {

  @Test
  public void documentedIdsResolveToTheirEmotion() {
    ExpressionEmotionTable table = ExpressionEmotionTable.load();
    // Every non-neutral chat-head expression seq harvested from the cache (generic block +
    // per-NPC).
    assertEquals("full non-neutral expression table is expected", 41, table.size());

    // Generic universal block: chathap=happy, chatscared/chatshock=scared, chatsad, chatang.
    assertEquals(Emotion.HAPPY, table.resolve(567));
    assertEquals(Emotion.SCARED, table.resolve(571));
    assertEquals(Emotion.SCARED, table.resolve(596));
    assertEquals(Emotion.HAPPY, table.resolve(605));
    assertEquals(Emotion.SAD, table.resolve(610));
    assertEquals(Emotion.ANGRY, table.resolve(614));
    // Per-NPC expression heads: lore_lizard_chat_happy/sad, kahlith_chat_disapproving.
    assertEquals(Emotion.HAPPY, table.resolve(4843));
    assertEquals(Emotion.SAD, table.resolve(4844));
    assertEquals(Emotion.ANGRY, table.resolve(8215));

    // A generic neutral expression (chatneu1) is intentionally absent and defaults to NEUTRAL.
    assertEquals(Emotion.NEUTRAL, table.resolve(588));
  }

  @Test
  public void unmappedIdResolvesToNeutral() {
    ExpressionEmotionTable table = ExpressionEmotionTable.load();
    // An id outside the documented set (e.g. a non-human head expression) is the default-NEUTRAL
    // case.
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
