package com.grahambartley.runelite.voiced.dialogue.audio;

import static com.grahambartley.runelite.voiced.dialogue.audio.TestPcm.sine;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class StreamingAudioPlayerTest {

  private static final int LOW_RATE = 1_000;

  private static float[] leadIn() {
    return new float[LOW_RATE * StreamingAudioPlayer.LEVELLING_LEAD_IN_MS / 1000];
  }

  private static SourceDataLine lineThatAcceptsEverything() {
    SourceDataLine line = mock(SourceDataLine.class);
    when(line.write(any(byte[].class), anyInt(), anyInt())).thenAnswer(inv -> inv.getArgument(2));
    return line;
  }

  @Test
  public void streamsAllPcmThenDrainsAndClosesLine() throws Exception {
    SourceDataLine line = lineThatAcceptsEverything();
    StreamingAudioPlayer player = new StreamingAudioPlayer(format -> line);

    player.stream(new float[] {0f, 0f, 0f}, 24_000, 100);

    ArgumentCaptor<AudioFormat> format = ArgumentCaptor.forClass(AudioFormat.class);
    verify(line).open(format.capture());
    assertEquals(24_000f, format.getValue().getSampleRate(), 0f);
    assertEquals(16, format.getValue().getSampleSizeInBits());
    assertEquals(1, format.getValue().getChannels());
    verify(line).start();
    verify(line).write(any(byte[].class), eq(0), eq(6));
    verify(line).drain();
    verify(line).close();
  }

  @Test
  public void stopMidStreamHaltsFurtherWritesAndSkipsDrain() {
    SourceDataLine line = mock(SourceDataLine.class);
    StreamingAudioPlayer player = new StreamingAudioPlayer(format -> line);
    when(line.write(any(byte[].class), anyInt(), anyInt()))
        .thenAnswer(
            inv -> {
              player.stop();
              return inv.getArgument(2);
            });

    player.stream(new float[5_000], 24_000, 100);

    verify(line, times(1)).write(any(byte[].class), anyInt(), anyInt());
    verify(line, never()).drain();
  }

  @Test
  public void streamedChunksOpenTheLineOnceThenDrainAndClose() throws Exception {
    SourceDataLine line = lineThatAcceptsEverything();
    StreamingAudioPlayer player = new StreamingAudioPlayer(format -> line);

    AudioOutput.AudioStream stream = player.beginStream(100);
    stream.write(leadIn(), LOW_RATE);
    stream.write(new float[] {0f, 0f}, LOW_RATE);
    stream.end();

    ArgumentCaptor<AudioFormat> format = ArgumentCaptor.forClass(AudioFormat.class);
    verify(line, timeout(2_000)).open(format.capture());
    assertEquals(LOW_RATE, format.getValue().getSampleRate(), 0f);
    verify(line, timeout(2_000)).start();
    verify(line, timeout(2_000).times(2)).write(any(byte[].class), anyInt(), anyInt());
    verify(line, timeout(2_000)).drain();
    verify(line, timeout(2_000)).close();
  }

  @Test
  public void anEmptyLeadingChunkDoesNotOpenTheLine() {
    StreamingAudioPlayer.LineFactory factory = mock(StreamingAudioPlayer.LineFactory.class);
    StreamingAudioPlayer player = new StreamingAudioPlayer(factory);

    AudioOutput.AudioStream stream = player.beginStream(100);
    stream.write(new float[0], 24_000);
    stream.write(null, 24_000);

    verifyNoInteractions(factory);
  }

  private static SourceDataLine lineSignalingWrites(CountDownLatch wrote) {
    SourceDataLine line = mock(SourceDataLine.class);
    when(line.write(any(byte[].class), anyInt(), anyInt()))
        .thenAnswer(
            inv -> {
              wrote.countDown();
              return inv.getArgument(2);
            });
    return line;
  }

  @Test
  public void stopMidStreamDropsRemainingChunksSkipsDrainAndReleases() throws Exception {
    CountDownLatch wrote = new CountDownLatch(1);
    SourceDataLine line = lineSignalingWrites(wrote);
    StreamingAudioPlayer player = new StreamingAudioPlayer(format -> line);

    AudioOutput.AudioStream stream = player.beginStream(100);
    stream.write(leadIn(), LOW_RATE);
    assertTrue("the first chunk reached the line", wrote.await(2, TimeUnit.SECONDS));
    player.stop();
    stream.write(new float[] {0f, 0f}, LOW_RATE);
    stream.end();

    verify(line, timeout(2_000)).close();
    verify(line, never()).drain();
    verify(line, times(1)).write(any(byte[].class), anyInt(), anyInt());
  }

  @Test
  public void aNewerLineSupersedesAnInProgressStream() throws Exception {
    CountDownLatch wrote = new CountDownLatch(1);
    SourceDataLine line = lineSignalingWrites(wrote);
    StreamingAudioPlayer player = new StreamingAudioPlayer(format -> line);

    AudioOutput.AudioStream first = player.beginStream(100);
    first.write(leadIn(), LOW_RATE);
    assertTrue(wrote.await(2, TimeUnit.SECONDS));
    player.beginStream(100);
    first.write(new float[] {0f, 0f}, LOW_RATE);
    first.end();

    verify(line, timeout(2_000)).close();
    verify(line, never()).drain();
    verify(line, times(1)).write(any(byte[].class), anyInt(), anyInt());
  }

  @Test
  public void emptySamplesNeverTouchTheAudioLine() {
    StreamingAudioPlayer.LineFactory factory = mock(StreamingAudioPlayer.LineFactory.class);
    StreamingAudioPlayer player = new StreamingAudioPlayer(factory);

    player.stream(new float[0], 24_000, 100);
    player.stream(null, 24_000, 100);

    verifyNoInteractions(factory);
  }

  @Test
  public void appliesProportionalGainWhenMasterGainIsSupported() {
    SourceDataLine line = lineThatAcceptsEverything();
    FloatControl gain = mock(FloatControl.class);
    when(gain.getMinimum()).thenReturn(-80f);
    when(gain.getMaximum()).thenReturn(6f);
    when(line.isControlSupported(FloatControl.Type.MASTER_GAIN)).thenReturn(true);
    when(line.getControl(FloatControl.Type.MASTER_GAIN)).thenReturn(gain);
    StreamingAudioPlayer player = new StreamingAudioPlayer(format -> line);

    player.stream(new float[] {0f}, 24_000, 50);

    ArgumentCaptor<Float> applied = ArgumentCaptor.forClass(Float.class);
    verify(gain).setValue(applied.capture());
    assertTrue(applied.getValue() <= 0f && applied.getValue() >= -80f);
  }

  @Test
  public void zeroVolumeSetsTheMinimumGain() {
    SourceDataLine line = lineThatAcceptsEverything();
    FloatControl gain = mock(FloatControl.class);
    when(gain.getMinimum()).thenReturn(-80f);
    when(line.isControlSupported(FloatControl.Type.MASTER_GAIN)).thenReturn(true);
    when(line.getControl(FloatControl.Type.MASTER_GAIN)).thenReturn(gain);
    StreamingAudioPlayer player = new StreamingAudioPlayer(format -> line);

    player.stream(new float[] {0f}, 24_000, 0);

    verify(gain).setValue(-80f);
  }

  @Test
  public void unsupportedGainControlDoesNotBreakPlayback() {
    SourceDataLine line = lineThatAcceptsEverything();
    when(line.isControlSupported(any(FloatControl.Type.class))).thenReturn(false);
    StreamingAudioPlayer player = new StreamingAudioPlayer(format -> line);

    player.stream(new float[] {0f}, 24_000, 100);

    verify(line).write(any(byte[].class), anyInt(), anyInt());
    verify(line).drain();
  }

  private static final float QUIET = 0.02f;

  private static int writtenPeak(SourceDataLine line, int writes) {
    ArgumentCaptor<byte[]> pcm = ArgumentCaptor.forClass(byte[].class);
    verify(line, timeout(2_000).times(writes)).write(pcm.capture(), anyInt(), anyInt());
    int peak = 0;
    for (byte[] written : pcm.getAllValues()) {
      peak = Math.max(peak, TestPcm.peak16(written));
    }
    return peak;
  }

  private static int unlevelledPeak() {
    return Math.round(QUIET * 32767f);
  }

  @Test
  public void aWholeClipIsLevelledBeforeItPlays() {
    SourceDataLine line = lineThatAcceptsEverything();
    StreamingAudioPlayer player = new StreamingAudioPlayer(format -> line);

    player.stream(sine(QUIET, 480), 24_000, 100);

    assertTrue(writtenPeak(line, 1) > unlevelledPeak() * 2);
  }

  @Test
  public void aStreamedLineHoldsTheLeadInGainForLaterChunks() {
    SourceDataLine line = lineThatAcceptsEverything();
    StreamingAudioPlayer player = new StreamingAudioPlayer(format -> line);
    float[] leadIn = sine(QUIET, leadIn().length);

    AudioOutput.AudioStream stream = player.beginStream(100);
    stream.write(leadIn, LOW_RATE);
    stream.write(sine(QUIET * 20, 20), LOW_RATE);
    stream.end();

    ArgumentCaptor<byte[]> pcm = ArgumentCaptor.forClass(byte[].class);
    verify(line, timeout(2_000).times(2)).write(pcm.capture(), anyInt(), anyInt());
    int leadInPeak = TestPcm.peak16(pcm.getAllValues().get(0));
    float gain = leadInPeak / (float) unlevelledPeak();
    assertTrue(gain > 2f);
    assertEquals(
        Math.min(32767, Math.round(QUIET * 20 * gain * 32767f)),
        TestPcm.peak16(pcm.getAllValues().get(1)),
        32767 * 0.02);
  }

  @Test
  public void aStreamedLineShorterThanTheLeadInIsStillLevelled() {
    SourceDataLine line = lineThatAcceptsEverything();
    StreamingAudioPlayer player = new StreamingAudioPlayer(format -> line);

    AudioOutput.AudioStream stream = player.beginStream(100);
    stream.write(sine(QUIET, 40), LOW_RATE);
    stream.end();

    assertTrue(writtenPeak(line, 1) > unlevelledPeak() * 2);
    verify(line, timeout(2_000)).drain();
  }
}
