package com.grahambartley.runelite.voiced.dialogue.synthesis;

/**
 * Stateful decoder for streamed headerless signed 16-bit little-endian mono PCM.
 *
 * <p>{@link RawPcmDecoder} decodes a complete buffer in one shot. When the audio is streamed off
 * the network instead, bytes arrive in arbitrary-sized chunks that can split a 16-bit sample across
 * a chunk boundary, so this decoder carries a single leftover low byte from one {@link #decode}
 * call into the next and emits only the whole samples completed so far. It is single-threaded by
 * design: one instance decodes one stream, fed by the one worker reading that response body.
 */
final class StreamingPcmDecoder {

  private static final float[] EMPTY = new float[0];

  /** The low byte of a sample whose high byte has not arrived yet, or {@code -1} when aligned. */
  private int pendingLowByte = -1;

  /**
   * Decodes the first {@code len} bytes of {@code chunk} to float samples in [-1, 1], pairing a low
   * byte carried from the previous call with this chunk's first byte when needed and carrying a
   * trailing odd byte into the next call. Returns an empty array (never {@code null}) when no whole
   * sample completes in this chunk.
   */
  float[] decode(byte[] chunk, int len) {
    if (chunk == null || len <= 0) {
      return EMPTY;
    }
    int totalBytes = len + (pendingLowByte >= 0 ? 1 : 0);
    int sampleCount = totalBytes / 2;
    if (sampleCount == 0) {
      // Only a single byte available (a carried byte with an empty-ish chunk, or one new byte with
      // none carried): keep it pending and emit nothing.
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

  /**
   * Whether a low byte is still buffered at end of stream, i.e. the stream carried an odd total
   * byte count and so is not a whole number of 16-bit samples (a truncated/corrupt body).
   */
  boolean hasPendingByte() {
    return pendingLowByte >= 0;
  }

  private static float sampleToFloat(int lo, int hi) {
    short s = (short) ((hi << 8) | (lo & 0xff));
    return s / 32768f;
  }
}
