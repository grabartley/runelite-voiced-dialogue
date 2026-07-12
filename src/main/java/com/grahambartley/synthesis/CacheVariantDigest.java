package com.grahambartley.synthesis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Collision-resistant compact digest for backend state folded into cache variants. */
final class CacheVariantDigest {

  private CacheVariantDigest() {}

  static String of(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(16);
      for (int i = 0; i < 8; i++) {
        hex.append(String.format("%02x", digest[i] & 0xff));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
