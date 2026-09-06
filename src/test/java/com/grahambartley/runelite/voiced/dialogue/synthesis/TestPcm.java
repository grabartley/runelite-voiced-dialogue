package com.grahambartley.runelite.voiced.dialogue.synthesis;

import java.io.ByteArrayOutputStream;

/** Builds raw PCM test bodies in the wire format the cloud speech endpoints return. */
final class TestPcm {

  private TestPcm() {}

  /** The raw, headerless little-endian byte stream for the given 16-bit samples. */
  static byte[] raw(short[] samples) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (short s : samples) {
      out.write(s & 0xff);
      out.write((s >> 8) & 0xff);
    }
    return out.toByteArray();
  }
}
