package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verifies the {@link ExpressionEmotionTable} loader and the documented default contract that the
 * emotion resolver and the backends depend on: a documented expression id returns its mapped {@link
 * Emotion}, while any unmapped id and {@code -1} resolve to {@link Emotion#NEUTRAL}. The bundled
 * resource's raw content is pinned by {@link ExpressionEmotionsResourceTest}.
 */
@RunWith(JUnitParamsRunner.class)
public class ExpressionEmotionTableTest {

  @Test
  public void loadedTableResolvesADocumentedId() {
    // 567 is chathap in the generic universal chat-head expression block.
    assertEquals(Emotion.HAPPY, ExpressionEmotionTable.load().resolve(567));
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
  public void aListedNeutralExpressionIdDefaultsToNeutral() {
    // A generic neutral expression (chatneu1) is intentionally absent and defaults to NEUTRAL.
    assertEquals(Emotion.NEUTRAL, ExpressionEmotionTable.load().resolve(588));
  }

  private Object[] neverNullIds() {
    return new Object[] {-1, 9760, 987654};
  }

  @Test
  @Parameters(method = "neverNullIds")
  public void resolveNeverReturnsNull(int id) {
    assertNotNull(ExpressionEmotionTable.load().resolve(id));
  }

  @Test
  public void parseSkipsDocKeysAndMapsIntegerIds() {
    JsonObject good =
        new JsonParser().parse("{\"_meta\":\"note\",\"9760\":\"HAPPY\"}").getAsJsonObject();
    Map<Integer, Emotion> parsed = ExpressionEmotionTable.parse(good);
    assertEquals(1, parsed.size());
    assertEquals(Emotion.HAPPY, parsed.get(9760));
  }

  @Test(expected = IllegalArgumentException.class)
  public void parseRejectsAnInvalidEmotionValue() {
    ExpressionEmotionTable.parse(
        new JsonParser().parse("{\"9760\":\"FURIOUS\"}").getAsJsonObject());
  }

  @Test(expected = IllegalArgumentException.class)
  public void parseRejectsANonIntegerIdKey() {
    ExpressionEmotionTable.parse(new JsonParser().parse("{\"nine\":\"HAPPY\"}").getAsJsonObject());
  }
}
