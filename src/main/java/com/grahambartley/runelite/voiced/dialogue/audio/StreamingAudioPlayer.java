package com.grahambartley.runelite.voiced.dialogue.audio;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StreamingAudioPlayer implements AudioOutput {

  public interface LineFactory {
    SourceDataLine getLine(AudioFormat format) throws LineUnavailableException;
  }

  private static final int CHUNK_BYTES = 4096;

  private static final int QUEUE_POLL_MS = 20;

  private final LineFactory lineFactory;
  private final AtomicLong generation = new AtomicLong();
  private volatile SourceDataLine line;

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
    byte[] pcm = PcmAudio.toPcm16LE(samples);
    SourceDataLine sdl = null;
    try {
      sdl = openLine(PcmAudio.format(sampleRate), volumePercent);
      writeChunked(sdl, pcm, gen);
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
    return new BufferedLineStream(gen, volumePercent);
  }

  @Override
  public void setVolume(int volumePercent) {
    SourceDataLine current = this.line;
    if (current == null) {
      return;
    }
    try {
      applyVolume(current, volumePercent);
    } catch (Exception ignored) {
    }
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
    private final int volumePercent;
    private final BlockingQueue<float[]> queue = new LinkedBlockingQueue<>();
    private volatile int sampleRate = -1;
    private volatile boolean ended;
    private boolean playerStarted;

    private BufferedLineStream(long gen, int volumePercent) {
      this.gen = gen;
      this.volumePercent = volumePercent;
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
        sdl = openLine(PcmAudio.format(sampleRate), volumePercent);
        while (generation.get() == gen) {
          float[] chunk = queue.poll(QUEUE_POLL_MS, TimeUnit.MILLISECONDS);
          if (chunk == null) {
            if (ended && queue.isEmpty()) {
              break;
            }
            continue;
          }
          writeChunked(sdl, PcmAudio.toPcm16LE(chunk), gen);
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
  }

  private SourceDataLine openLine(AudioFormat format, int volumePercent)
      throws LineUnavailableException {
    SourceDataLine sdl = lineFactory.getLine(format);
    boolean opened = false;
    try {
      sdl.open(format);
      applyVolume(sdl, volumePercent);
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

  private void writeChunked(SourceDataLine sdl, byte[] pcm, long gen) {
    int offset = 0;
    while (offset < pcm.length && generation.get() == gen) {
      offset += sdl.write(pcm, offset, Math.min(CHUNK_BYTES, pcm.length - offset));
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

  private static void applyVolume(SourceDataLine line, int volumePercent) {
    if (!line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
      return;
    }
    FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
    int volume = Math.max(0, Math.min(100, volumePercent));
    if (volume == 0) {
      gain.setValue(gain.getMinimum());
      return;
    }
    float db = (float) (20.0 * Math.log10(volume / 100.0));
    gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
  }
}
