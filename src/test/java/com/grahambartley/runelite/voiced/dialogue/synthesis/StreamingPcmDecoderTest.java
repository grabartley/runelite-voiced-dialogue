package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Incremental decoding of streamed PCM, including 16-bit samples split across chunk boundaries. */
public class StreamingPcmDecoderTest {

  /**
   * Feeds {@code raw} to one decoder in the given chunk sizes and concatenates every emitted
   * sample.
   */
  private static float[] decodeInChunks(byte[] raw, int... chunkSizes) {
    StreamingPcmDecoder dec = new StreamingPcmDecoder();
    List<Float> all = new ArrayList<>();
    int off = 0;
    for (int size : chunkSizes) {
      int len = Math.min(size, raw.length - off);
      byte[] chunk = new byte[len];
      System.arraycopy(raw, off, chunk, 0, len);
      for (float f : dec.decode(chunk, len)) {
        all.add(f);
      }
      off += len;
    }
    float[] out = new float[all.size()];
    for (int i = 0; i < out.length; i++) {
      out[i] = all.get(i);
    }
    return out;
  }

  @Test
  public void reassemblesSamplesSplitAcrossChunkBoundaries() {
    short[] samples = {0, 16384, -16384, 32767, -32768, 1000, -1000, 12345};
    byte[] raw = TestPcm.raw(samples); // 16 bytes
    // Chunk sizes chosen to split samples across boundaries: 1 | 3 | 2 | 5 | 5.
    float[] streamed = decodeInChunks(raw, 1, 3, 2, 5, 5);
    float[] whole = RawPcmDecoder.decode(raw, 24_000).getSamples();
    assertArrayEquals("streamed decode matches whole-buffer decode", whole, streamed, 1e-6f);
  }

  @Test
  public void handlesEverySampleStraddlingTwoChunks() {
    short[] samples = new short[64];
    for (int i = 0; i < samples.length; i++) {
      samples[i] = (short) (i * 500 - 16000);
    }
    byte[] raw = TestPcm.raw(samples);
    int[] oneByteEach = new int[raw.length];
    Arrays.fill(oneByteEach, 1);
    // Worst case: one byte per call, so every sample straddles two decode() calls.
    float[] streamed = decodeInChunks(raw, oneByteEach);
    float[] whole = RawPcmDecoder.decode(raw, 24_000).getSamples();
    assertArrayEquals(whole, streamed, 1e-6f);
  }

  @Test
  public void aWholeNumberOfSamplesLeavesNoPendingByte() {
    StreamingPcmDecoder dec = new StreamingPcmDecoder();
    byte[] raw = TestPcm.raw(new short[] {1, 2, 3});
    dec.decode(raw, raw.length);
    assertFalse("an even byte count is a whole number of samples", dec.hasPendingByte());
  }

  @Test
  public void anOddTrailingByteIsHeldAndReportedAsPending() {
    StreamingPcmDecoder dec = new StreamingPcmDecoder();
    byte[] whole = TestPcm.raw(new short[] {1, 2}); // 4 bytes
    byte[] withOdd = Arrays.copyOf(whole, whole.length + 1);
    withOdd[whole.length] = 0x7f; // a lone trailing low byte
    float[] out = dec.decode(withOdd, withOdd.length);
    assertEquals("only the whole samples are emitted", 2, out.length);
    assertTrue("the dangling byte is held pending (a truncated stream)", dec.hasPendingByte());
  }

  @Test
  public void emptyOrNullChunksEmitNothingAndKeepState() {
    StreamingPcmDecoder dec = new StreamingPcmDecoder();
    assertEquals(0, dec.decode(new byte[0], 0).length);
    assertEquals(0, dec.decode(null, 5).length);
    assertFalse(dec.hasPendingByte());
    dec.decode(new byte[] {0x10}, 1); // a lone byte with nothing to pair it
    assertTrue(dec.hasPendingByte());
    assertEquals("an empty chunk emits nothing", 0, dec.decode(new byte[0], 0).length);
    assertTrue("and does not drop the pending byte", dec.hasPendingByte());
  }
}
