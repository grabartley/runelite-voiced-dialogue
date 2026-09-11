package com.grahambartley.runelite.voiced.dialogue.audio;

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
    stream.write(new float[] {0f, 0f, 0f}, 24_000);
    stream.write(new float[] {0f, 0f}, 24_000);
    stream.end();

    ArgumentCaptor<AudioFormat> format = ArgumentCaptor.forClass(AudioFormat.class);
    verify(line, timeout(2_000)).open(format.capture());
    assertEquals(24_000f, format.getValue().getSampleRate(), 0f);
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
    stream.write(new float[] {0f, 0f}, 24_000);
    assertTrue("the first chunk reached the line", wrote.await(2, TimeUnit.SECONDS));
    player.stop();
    stream.write(new float[] {0f, 0f}, 24_000);
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
    first.write(new float[] {0f, 0f}, 24_000);
    assertTrue(wrote.await(2, TimeUnit.SECONDS));
    player.beginStream(100);
    first.write(new float[] {0f, 0f}, 24_000);
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
}
