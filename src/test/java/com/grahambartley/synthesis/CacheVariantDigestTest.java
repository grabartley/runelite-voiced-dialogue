package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class CacheVariantDigestTest {

  @Test
  public void isStableAndSeparatesKnownJavaHashCollision() {
    assertEquals(CacheVariantDigest.of("Aa"), CacheVariantDigest.of("Aa"));
    assertNotEquals(CacheVariantDigest.of("Aa"), CacheVariantDigest.of("BB"));
  }
}
