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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class StreamingAudioPlayerTest {

  private static final int LOW_RATE = 1_000;
  private static final int CHUNK = 20;
  private static final float QUIET = 0.02f;
  private static final int UNLEVELLED_PEAK = Math.round(QUIET * 32767f);

  private static List<Integer> writtenPeaks(SourceDataLine line, int writes) {
    ArgumentCaptor<byte[]> pcm = ArgumentCaptor.forClass(byte[].class);
    verify(line, timeout(2_000).times(writes)).write(pcm.capture(), anyInt(), anyInt());
    List<Integer> peaks = new ArrayList<>();
    for (byte[] written : pcm.getAllValues()) {
      peaks.add(TestPcm.peak16(written));
    }
    return peaks;
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
    stream.write(new float[] {0f, 0f, 0f}, LOW_RATE);
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
    stream.write(new float[] {0f, 0f, 0f}, LOW_RATE);
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
    first.write(new float[] {0f, 0f}, LOW_RATE);
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
  public void theVolumeScalesWhatIsWritten() {
    SourceDataLine full = lineThatAcceptsEverything();
    SourceDataLine half = lineThatAcceptsEverything();

    new StreamingAudioPlayer(format -> full).stream(sine(QUIET, 480), 24_000, 100);
    new StreamingAudioPlayer(format -> half).stream(sine(QUIET, 480), 24_000, 50);

    assertEquals(writtenPeaks(full, 1).get(0) / 2.0, writtenPeaks(half, 1).get(0), 2);
  }

  @Test
  public void zeroVolumeWritesSilence() {
    SourceDataLine line = lineThatAcceptsEverything();

    new StreamingAudioPlayer(format -> line).stream(sine(QUIET, 480), 24_000, 0);

    assertEquals(0, (int) writtenPeaks(line, 1).get(0));
  }

  @Test
  public void aVolumeChangeReachesTheRestOfAPlayingLine() {
    SourceDataLine line = mock(SourceDataLine.class);
    StreamingAudioPlayer player = new StreamingAudioPlayer(format -> line);
    when(line.write(any(byte[].class), anyInt(), anyInt()))
        .thenAnswer(
            inv -> {
              player.setVolume(0);
              return inv.getArgument(2);
            });

    player.stream(sine(QUIET, 5_000), 24_000, 100);

    List<Integer> peaks = writtenPeaks(line, 3);
    assertTrue(peaks.get(0) > 0);
    assertEquals(0, (int) peaks.get(1));
    assertEquals(0, (int) peaks.get(2));
  }

  @Test
  public void theLineGainControlIsNeverTouched() {
    SourceDataLine line = lineThatAcceptsEverything();

    new StreamingAudioPlayer(format -> line).stream(sine(QUIET, 480), 24_000, 50);

    verify(line, never()).getControl(any());
    verify(line).drain();
  }

  @Test
  public void aWholeClipIsLevelledBeforeItPlays() {
    SourceDataLine line = lineThatAcceptsEverything();
    StreamingAudioPlayer player = new StreamingAudioPlayer(format -> line);

    player.stream(sine(QUIET, 480), 24_000, 100);

    assertTrue(writtenPeaks(line, 1).get(0) > UNLEVELLED_PEAK * 2);
  }

  private static AudioOutput.AudioStream streamOf(SourceDataLine line, int volume) {
    return new StreamingAudioPlayer(format -> line).beginStream(volume);
  }

  private static void writeQuietChunks(AudioOutput.AudioStream stream, int count) {
    for (int i = 0; i < count; i++) {
      stream.write(sine(QUIET, CHUNK), LOW_RATE);
    }
  }

  @Test
  public void aStreamedLineStartsAtItsRawLevelWithoutWaiting() {
    SourceDataLine line = lineThatAcceptsEverything();
    AudioOutput.AudioStream stream = streamOf(line, 100);

    stream.write(sine(QUIET, CHUNK), LOW_RATE);

    assertEquals(UNLEVELLED_PEAK, writtenPeaks(line, 1).get(0), UNLEVELLED_PEAK * 0.02);
    stream.end();
  }

  @Test
  public void aStreamedLineSlidesGentlyTowardsTheLevel() {
    SourceDataLine line = lineThatAcceptsEverything();
    AudioOutput.AudioStream stream = streamOf(line, 100);
    int oneSecondOfChunks = LOW_RATE / CHUNK;

    writeQuietChunks(stream, oneSecondOfChunks);
    stream.end();

    List<Integer> peaks = writtenPeaks(line, oneSecondOfChunks);
    double risenDb = 20 * Math.log10(peaks.get(peaks.size() - 1) / (double) peaks.get(0));
    assertEquals(3.0, risenDb, 0.3);
  }

  @Test
  public void theVolumeHoldsThroughoutTheSlide() {
    SourceDataLine full = lineThatAcceptsEverything();
    SourceDataLine fifth = lineThatAcceptsEverything();
    AudioOutput.AudioStream fullStream = streamOf(full, 100);
    AudioOutput.AudioStream fifthStream = streamOf(fifth, 20);
    int chunks = 2 * LOW_RATE / CHUNK;

    for (int i = 0; i < chunks; i++) {
      float[] chunk = sine(QUIET, CHUNK);
      fullStream.write(chunk, LOW_RATE);
      fifthStream.write(chunk, LOW_RATE);
    }
    fullStream.end();
    fifthStream.end();

    List<Integer> fullPeaks = writtenPeaks(full, chunks);
    List<Integer> fifthPeaks = writtenPeaks(fifth, chunks);
    for (int i = 0; i < chunks; i++) {
      assertEquals(fullPeaks.get(i) * 0.2, fifthPeaks.get(i), 1.5);
    }
  }

  @Test
  public void aStreamedLineThatSwellsIsLoweredRatherThanClipped() {
    SourceDataLine line = lineThatAcceptsEverything();
    AudioOutput.AudioStream stream = streamOf(line, 100);

    writeQuietChunks(stream, 3 * LOW_RATE / CHUNK);
    stream.write(sine(QUIET * 40, CHUNK), LOW_RATE);
    stream.end();

    List<Integer> peaks = writtenPeaks(line, 3 * LOW_RATE / CHUNK + 1);
    int ceiling = Math.round((float) Math.pow(10, -1.0 / 20) * 32767f);
    assertTrue(peaks.get(peaks.size() - 1) <= ceiling + 1);
  }
}
