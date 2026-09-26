package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DirectionSanitizerTest {

  private final DirectionSanitizer sanitizer = new DirectionSanitizer(new ProfanityFilter());

  @Test
  public void flattensNewlinesIntoASingleLineField() {
    String clean = sanitizer.sanitize("A pirate.\n\nGruff\r\nand loud.");
    assertEquals("A pirate. Gruff and loud.", clean);
  }

  @Test
  public void stripsSquareAndAngleTagBrackets() {
    String clean = sanitizer.sanitize("[angry] gruff <laugh> and loud");
    assertEquals("angry gruff laugh and loud", clean);
  }

  @Test
  public void collapsesRepeatedWhitespaceAndTrims() {
    assertEquals("A gruff dwarf.", sanitizer.sanitize("  A   gruff\tdwarf.  "));
  }

  @Test
  public void capsOnlyAHostilePasteAndLeavesRealisticDescriptionsIntact() {
    StringBuilder hostile = new StringBuilder();
    for (int i = 0; i < 5_000; i++) {
      hostile.append("a");
    }
    assertTrue(
        "a pathological paste is bounded by the defensive ceiling",
        sanitizer.sanitize(hostile.toString()).length() <= DirectionSanitizer.MAX_FIELD_LENGTH);

    String realistic =
        "A weathered sea captain from the northern isles, gruff but warm, who has seen too many"
            + " storms and speaks in slow, deliberate sentences with a dry, knowing humour that"
            + " surfaces only when he trusts you, and never wastes a word he does not mean.";
    assertEquals(
        "a normal multi-sentence description is never truncated",
        realistic,
        sanitizer.sanitize(realistic));
  }

  @Test
  public void masksProfanityTypedIntoAField() {
    assertEquals(
        "a foul-mouthed **** of a pirate", sanitizer.sanitize("a foul-mouthed twat of a pirate"));
  }

  @Test
  public void outputIsByteStableForCacheSafety() {
    String input = "A  noble  knight.\nSpeaks with conviction.";
    assertEquals(
        "the same input always sanitizes to the same output (the profile cache key stays stable)",
        sanitizer.sanitize(input),
        sanitizer.sanitize(input));
  }

  @Test
  public void nullPassesThrough() {
    assertEquals(null, sanitizer.sanitize(null));
  }
}
