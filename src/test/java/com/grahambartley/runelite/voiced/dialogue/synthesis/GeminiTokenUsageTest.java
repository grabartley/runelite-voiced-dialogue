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
    return GeminiTokenUsage.forSpeech(gson, raw);
  }

  @Test
  public void takesAudioTokensFromThePerModalityBreakdown() {
    GeminiTokenUsage usage =
        parse(
            "{\"usageMetadata\":{\"promptTokenCount\":42,\"candidatesTokenCount\":1700,"
                + "\"candidatesTokensDetails\":[{\"modality\":\"AUDIO\",\"tokenCount\":1700}]}}");

    assertEquals(1700, usage.audioTokens);
    assertEquals(42, usage.promptTokens);
    assertEquals("a speech call has no text output", 0, usage.textTokens);
    assertFalse(usage.isEmpty());
  }

  @Test
  public void fallsBackToTheCandidateTotalWhenNoBreakdownIsGiven() {
    GeminiTokenUsage usage =
        parse("{\"usageMetadata\":{\"promptTokenCount\":42,\"candidatesTokenCount\":1700}}");

    assertEquals("a speech call's candidates are audio in their entirety", 1700, usage.audioTokens);
    assertEquals(42, usage.promptTokens);
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
  public void aTextCallBillsItsOutputAsTextRatherThanAudio() {
    GeminiTokenUsage usage =
        GeminiTokenUsage.forText(
            gson, "{\"usageMetadata\":{\"promptTokenCount\":90,\"candidatesTokenCount\":75}}");

    assertEquals(90, usage.promptTokens);
    assertEquals(75, usage.textTokens);
    assertEquals(
        "reading the hop's output as audio would misprice it by more than twenty times",
        0,
        usage.audioTokens);
  }

  @Test
  public void anAbsentBlockOnATextCallReportsNothing() {
    assertTrue(GeminiTokenUsage.forText(gson, "{}").isEmpty());
    assertTrue(GeminiTokenUsage.forText(gson, "not json").isEmpty());
  }

  @Test
  public void negativeCountsAreFloored() {
    GeminiTokenUsage usage =
        parse("{\"usageMetadata\":{\"promptTokenCount\":-5,\"candidatesTokenCount\":-9}}");

    assertTrue(usage.isEmpty());
  }

  @Test
  public void maxKeepsTheLargestReadingSoACumulativeStreamIsNeverDoubleCounted() {
    GeminiTokenUsage first = new GeminiTokenUsage(42, 400, 0);
    GeminiTokenUsage running = new GeminiTokenUsage(42, 1700, 0);

    GeminiTokenUsage combined = first.max(running);

    assertEquals(1700, combined.audioTokens);
    assertEquals(42, combined.promptTokens);
    assertEquals("a null reading leaves the total alone", 1700, combined.max(null).audioTokens);
  }
}
