package com.grahambartley.runelite.voiced.dialogue.audio;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StreamingAudioPlayer implements AudioOutput {

  public interface LineFactory {
    SourceDataLine getLine(AudioFormat format) throws LineUnavailableException;
  }

  private static final int WRITE_BLOCK_SAMPLES = 2048;

  private static final int QUEUE_POLL_MS = 20;

  private final LineFactory lineFactory;
  private final AtomicLong generation = new AtomicLong();
  private volatile SourceDataLine line;
  private volatile int volumePercent;

  public StreamingAudioPlayer() {
    this(AudioSystem::getSourceDataLine);
  }

  StreamingAudioPlayer(LineFactory lineFactory) {
    this.lineFactory = lineFactory;
  }

  @Override
  public void stream(float[] samples, int sampleRate, int volumePercent) {
    if (samples == null || samples.length == 0) {
      return;
    }
    long gen = generation.incrementAndGet();
    this.volumePercent = volumePercent;
    LoudnessLeveller leveller = LoudnessLeveller.measure(samples, sampleRate);
    SourceDataLine sdl = null;
    try {
      sdl = openLine(PcmAudio.format(sampleRate));
      writeLevelled(sdl, samples, leveller, gen);
      if (generation.get() == gen) {
        sdl.drain();
      }
    } catch (Exception e) {
      log.warn("Audio playback failed: {}", e.getMessage());
    } finally {
      if (sdl != null) {
        releaseLine(sdl);
      }
    }
  }

  @Override
  public AudioStream beginStream(int volumePercent) {
    long gen = generation.incrementAndGet();
    this.volumePercent = volumePercent;
    return new BufferedLineStream(gen);
  }

  @Override
  public void setVolume(int volumePercent) {
    this.volumePercent = volumePercent;
  }

  @Override
  public void stop() {
    generation.incrementAndGet();
    SourceDataLine current = this.line;
    if (current != null) {
      try {
        current.stop();
        current.flush();
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  public void close() {
    stop();
  }

  private final class BufferedLineStream implements AudioStream {
    private final long gen;
    private final BlockingQueue<float[]> queue = new LinkedBlockingQueue<>();
    private volatile int sampleRate = -1;
    private volatile boolean ended;
    private boolean playerStarted;

    private BufferedLineStream(long gen) {
      this.gen = gen;
    }

    @Override
    public void write(float[] samples, int sampleRate) {
      if (samples == null || samples.length == 0 || generation.get() != gen) {
        return;
      }
      if (this.sampleRate < 0) {
        this.sampleRate = sampleRate;
      }
      queue.offer(samples);
      startPlayer();
    }

    @Override
    public void end() {
      ended = true;
      startPlayer();
    }

    private synchronized void startPlayer() {
      if (playerStarted || sampleRate < 0) {
        return;
      }
      playerStarted = true;
      Thread player = new Thread(this::playLoop, "dialogue-audio-play");
      player.setDaemon(true);
      player.start();
    }

    private void playLoop() {
      SourceDataLine sdl = null;
      try {
        sdl = openLine(PcmAudio.format(sampleRate));
        LoudnessLeveller leveller = LoudnessLeveller.streaming(sampleRate);
        float[] chunk;
        while ((chunk = nextChunk()) != null) {
          leveller.include(chunk);
          writeLevelled(sdl, chunk, leveller, gen);
        }
        if (generation.get() == gen) {
          sdl.drain();
        }
      } catch (Exception e) {
        log.warn("Audio streaming failed: {}", e.getMessage());
      } finally {
        if (sdl != null) {
          releaseLine(sdl);
        }
      }
    }

    private float[] nextChunk() throws InterruptedException {
      while (generation.get() == gen) {
        float[] chunk = queue.poll(QUEUE_POLL_MS, TimeUnit.MILLISECONDS);
        if (chunk != null) {
          return chunk;
        }
        if (ended && queue.isEmpty()) {
          return null;
        }
      }
      return null;
    }
  }

  private SourceDataLine openLine(AudioFormat format) throws LineUnavailableException {
    SourceDataLine sdl = lineFactory.getLine(format);
    boolean opened = false;
    try {
      sdl.open(format);
      sdl.start();
      this.line = sdl;
      opened = true;
      return sdl;
    } finally {
      if (!opened) {
        releaseLine(sdl);
      }
    }
  }

  private void writeLevelled(
      SourceDataLine sdl, float[] samples, LoudnessLeveller leveller, long gen) {
    for (int from = 0;
        from < samples.length && generation.get() == gen;
        from += WRITE_BLOCK_SAMPLES) {
      int to = Math.min(samples.length, from + WRITE_BLOCK_SAMPLES);
      byte[] pcm = PcmAudio.toPcm16LE(samples, from, to, leveller.gainAt(volumePercent));
      int offset = 0;
      while (offset < pcm.length && generation.get() == gen) {
        offset += sdl.write(pcm, offset, pcm.length - offset);
      }
    }
  }

  private void releaseLine(SourceDataLine sdl) {
    try {
      sdl.stop();
      sdl.close();
    } catch (Exception ignored) {
    }
    if (this.line == sdl) {
      this.line = null;
    }
  }
}
