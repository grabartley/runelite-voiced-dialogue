package com.grahambartley.runelite.voiced.dialogue.audio;

public final class StreamingPcmDecoder {

  private static final float[] EMPTY = new float[0];

  private int pendingLowByte = -1;

  public float[] decode(byte[] chunk, int len) {
    if (chunk == null || len <= 0) {
      return EMPTY;
    }
    int totalBytes = len + (pendingLowByte >= 0 ? 1 : 0);
    int sampleCount = totalBytes / 2;
    if (sampleCount == 0) {
      if (pendingLowByte < 0) {
        pendingLowByte = chunk[0] & 0xff;
      }
      return EMPTY;
    }
    float[] out = new float[sampleCount];
    int bi = 0;
    int oi = 0;
    if (pendingLowByte >= 0) {
      out[oi++] = sampleToFloat(pendingLowByte, chunk[bi++]);
      pendingLowByte = -1;
    }
    while (len - bi >= 2) {
      out[oi++] = sampleToFloat(chunk[bi] & 0xff, chunk[bi + 1]);
      bi += 2;
    }
    if (len - bi == 1) {
      pendingLowByte = chunk[bi] & 0xff;
    }
    return out;
  }

  public boolean hasPendingByte() {
    return pendingLowByte >= 0;
  }

  private static float sampleToFloat(int lo, int hi) {
    short s = (short) ((hi << 8) | (lo & 0xff));
    return s / 32768f;
  }
}
