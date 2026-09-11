package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.google.gson.Gson;
import org.junit.Test;

public class AiStudioTokenUsageTest {

  private final Gson gson = new Gson();

  private AiStudioTokenUsage parse(String raw) {
    return AiStudioTokenUsage.forSpeech(gson, raw);
  }

  @Test
  public void takesAudioTokensFromThePerModalityBreakdown() {
    AiStudioTokenUsage usage =
        parse(
            "{\"usageMetadata\":{\"promptTokenCount\":42,\"candidatesTokenCount\":1700,"
                + "\"candidatesTokensDetails\":[{\"modality\":\"AUDIO\",\"tokenCount\":1700}]}}");

    assertEquals(1700, usage.audioTokens);
    assertEquals(42, usage.promptTokens);
    assertEquals("a speech call has no text output", 0, usage.textTokens);
  }

  @Test
  public void fallsBackToTheCandidateTotalWhenNoBreakdownIsGiven() {
    AiStudioTokenUsage usage =
        parse("{\"usageMetadata\":{\"promptTokenCount\":42,\"candidatesTokenCount\":1700}}");

    assertEquals("a speech call's candidates are audio in their entirety", 1700, usage.audioTokens);
    assertEquals(42, usage.promptTokens);
  }

  @Test
  public void sumsEveryAudioEntryAndIgnoresOtherModalities() {
    AiStudioTokenUsage usage =
        parse(
            "{\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokensDetails\":["
                + "{\"modality\":\"AUDIO\",\"tokenCount\":600},"
                + "{\"modality\":\"TEXT\",\"tokenCount\":99},"
                + "{\"modality\":\"AUDIO\",\"tokenCount\":400}]}}");

    assertEquals(1000, usage.audioTokens);
  }

  @Test
  public void anUnrecognisedOrAbsentBlockReportsNothingRatherThanAWrongNumber() {
    assertSame(AiStudioTokenUsage.NONE, parse(null));
    assertSame(AiStudioTokenUsage.NONE, parse(""));
    assertSame(AiStudioTokenUsage.NONE, parse("not json"));
    assertSame(AiStudioTokenUsage.NONE, parse("{}"));
    assertSame(AiStudioTokenUsage.NONE, parse("{\"usageMetadata\":{}}"));
    assertSame(AiStudioTokenUsage.NONE, parse("{\"candidates\":[]}"));
  }

  @Test
  public void aTextCallBillsItsOutputAsTextRatherThanAudio() {
    AiStudioTokenUsage usage =
        AiStudioTokenUsage.forText(
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
    assertSame(AiStudioTokenUsage.NONE, AiStudioTokenUsage.forText(gson, "{}"));
    assertSame(AiStudioTokenUsage.NONE, AiStudioTokenUsage.forText(gson, "not json"));
  }

  @Test
  public void negativeCountsAreFloored() {
    AiStudioTokenUsage usage =
        parse("{\"usageMetadata\":{\"promptTokenCount\":-5,\"candidatesTokenCount\":-9}}");

    assertSame(AiStudioTokenUsage.NONE, usage);
  }

  @Test
  public void maxKeepsTheLargestReadingSoACumulativeStreamIsNeverDoubleCounted() {
    AiStudioTokenUsage first = new AiStudioTokenUsage(42, 400, 0);
    AiStudioTokenUsage running = new AiStudioTokenUsage(42, 1700, 0);

    AiStudioTokenUsage combined = first.max(running);

    assertEquals(1700, combined.audioTokens);
    assertEquals(42, combined.promptTokens);
    assertEquals("a null reading leaves the total alone", 1700, combined.max(null).audioTokens);
  }
}
