package com.grahambartley.runelite.voiced.dialogue.audio;

import static org.junit.Assert.assertEquals;

import javax.sound.sampled.AudioFormat;
import org.junit.Test;

public class PcmAudioTest {

  @Test
  public void producesTwoBytesPerSample() {
    byte[] pcm = PcmAudio.toPcm16LE(new float[] {0f, 0f, 0f});
    assertEquals(6, pcm.length);
  }

  @Test
  public void encodesSilenceAsZero() {
    byte[] pcm = PcmAudio.toPcm16LE(new float[] {0f});
    assertEquals(0, pcm[0]);
    assertEquals(0, pcm[1]);
  }

  @Test
  public void usesLittleEndianByteOrder() {
    byte[] pcm = PcmAudio.toPcm16LE(new float[] {1f});
    assertEquals((byte) 0xFF, pcm[0]);
    assertEquals((byte) 0x7F, pcm[1]);
  }

  @Test
  public void clampsValuesAboveOne() {
    byte[] pcm = PcmAudio.toPcm16LE(new float[] {2f});
    assertEquals((byte) 0xFF, pcm[0]);
    assertEquals((byte) 0x7F, pcm[1]);
  }

  @Test
  public void clampsValuesBelowNegativeOne() {
    byte[] pcm = PcmAudio.toPcm16LE(new float[] {-5f});
    assertEquals((byte) 0x01, pcm[0]);
    assertEquals((byte) 0x80, pcm[1]);
  }

  @Test
  public void formatIsMono16BitSignedLittleEndian() {
    AudioFormat format = PcmAudio.format(24_000);
    assertEquals(24_000f, format.getSampleRate(), 0.0f);
    assertEquals(16, format.getSampleSizeInBits());
    assertEquals(1, format.getChannels());
    assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
    assertEquals(false, format.isBigEndian());
  }
}
