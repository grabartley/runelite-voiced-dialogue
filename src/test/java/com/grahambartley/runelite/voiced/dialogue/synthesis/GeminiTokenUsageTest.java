package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import org.junit.Test;

/** Reading the token counts Google AI Studio reports for a speech call. */
public class GeminiTokenUsageTest {

  private final Gson gson = new Gson();

  private GeminiTokenUsage parse(String raw) {
    return GeminiTokenUsage.parse(gson, raw);
  }

  @Test
  public void takesAudioTokensFromThePerModalityBreakdown() {
    GeminiTokenUsage usage =
        parse(
            "{\"usageMetadata\":{\"promptTokenCount\":42,\"candidatesTokenCount\":1700,"
                + "\"candidatesTokensDetails\":[{\"modality\":\"AUDIO\",\"tokenCount\":1700}]}}");

    assertEquals(1700, usage.audioTokens);
    assertEquals(42, usage.textTokens);
    assertFalse(usage.isEmpty());
  }

  @Test
  public void fallsBackToTheCandidateTotalWhenNoBreakdownIsGiven() {
    GeminiTokenUsage usage =
        parse("{\"usageMetadata\":{\"promptTokenCount\":42,\"candidatesTokenCount\":1700}}");

    assertEquals("a speech call's candidates are audio in their entirety", 1700, usage.audioTokens);
    assertEquals(42, usage.textTokens);
  }

  @Test
  public void sumsEveryAudioEntryAndIgnoresOtherModalities() {
    GeminiTokenUsage usage =
        parse(
            "{\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokensDetails\":["
                + "{\"modality\":\"AUDIO\",\"tokenCount\":600},"
                + "{\"modality\":\"TEXT\",\"tokenCount\":99},"
                + "{\"modality\":\"AUDIO\",\"tokenCount\":400}]}}");

    assertEquals(1000, usage.audioTokens);
  }

  @Test
  public void anUnrecognisedOrAbsentBlockReportsNothingRatherThanAWrongNumber() {
    assertTrue(parse(null).isEmpty());
    assertTrue(parse("").isEmpty());
    assertTrue(parse("not json").isEmpty());
    assertTrue(parse("{}").isEmpty());
    assertTrue(parse("{\"usageMetadata\":{}}").isEmpty());
    assertTrue(parse("{\"candidates\":[]}").isEmpty());
  }

  @Test
  public void negativeCountsAreFloored() {
    GeminiTokenUsage usage =
        parse("{\"usageMetadata\":{\"promptTokenCount\":-5,\"candidatesTokenCount\":-9}}");

    assertTrue(usage.isEmpty());
  }

  @Test
  public void maxKeepsTheLargestReadingSoACumulativeStreamIsNeverDoubleCounted() {
    GeminiTokenUsage first = new GeminiTokenUsage(400, 42);
    GeminiTokenUsage running = new GeminiTokenUsage(1700, 42);

    GeminiTokenUsage combined = first.max(running);

    assertEquals(1700, combined.audioTokens);
    assertEquals(42, combined.textTokens);
    assertEquals("a null reading leaves the total alone", 1700, combined.max(null).audioTokens);
  }
}
