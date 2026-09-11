package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class ExpressionEmotionTableTest {

  @Test
  public void loadedTableResolvesADocumentedId() {
    assertEquals(Emotion.HAPPY, ExpressionEmotionTable.load().resolve(567));
  }

  @Test
  public void unmappedIdResolvesToNeutral() {
    ExpressionEmotionTable table = ExpressionEmotionTable.load();
    assertEquals(Emotion.NEUTRAL, table.resolve(123456));
  }

  @Test
  public void negativeOneResolvesToNeutral() {
    ExpressionEmotionTable table = ExpressionEmotionTable.load();
    assertEquals(Emotion.NEUTRAL, table.resolve(-1));
  }

  @Test
  public void aListedNeutralExpressionIdDefaultsToNeutral() {
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
