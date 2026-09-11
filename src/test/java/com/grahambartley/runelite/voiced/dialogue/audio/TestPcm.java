package com.grahambartley.runelite.voiced.dialogue.audio;

import java.io.ByteArrayOutputStream;

public final class TestPcm {

  private TestPcm() {}

  public static byte[] raw(short[] samples) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (short s : samples) {
      out.write(s & 0xff);
      out.write((s >> 8) & 0xff);
    }
    return out.toByteArray();
  }
}
