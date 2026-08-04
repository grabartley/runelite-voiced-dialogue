package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The compact digest folded into cache identities. */
public class CacheVariantDigestTest {

  @Test
  public void identicalInputProducesTheSameDigest() {
    assertEquals(CacheVariantDigest.of("Wizard|British"), CacheVariantDigest.of("Wizard|British"));
  }

  @Test
  public void differentInputProducesADifferentDigest() {
    assertNotEquals(CacheVariantDigest.of("Wizard|British"), CacheVariantDigest.of("Wizard|Irish"));
  }

  @Test
  public void digestIsShortAndFilesystemSafeHex() {
    String digest = CacheVariantDigest.of("Wizard|British");

    assertEquals(16, digest.length());
    assertTrue(digest, digest.matches("[0-9a-f]{16}"));
  }
}
