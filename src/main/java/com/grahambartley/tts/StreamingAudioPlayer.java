package com.grahambartley.tts;

import java.util.concurrent.atomic.AtomicLong;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import lombok.extern.slf4j.Slf4j;

/**
 * Streams synthesized PCM through a {@link SourceDataLine} instead of loading a whole {@code Clip}.
 *
 * <p>Audio is written to the line in small chunks straight from memory, so nothing is ever staged
 * to a temp file on disk. A generation counter lets {@link #stop()} interrupt the in-flight line:
 * the streaming loop bails as soon as a newer generation is observed.
 */
@Slf4j
public class StreamingAudioPlayer implements AudioOutput {

  /**
   * Supplies an unopened {@link SourceDataLine} for a format; a seam so tests can mock the line.
   */
  public interface LineFactory {
    SourceDataLine getLine(AudioFormat format) throws LineUnavailableException;
  }

  private static final int CHUNK_BYTES = 4096;

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
    AudioFormat format = PcmAudio.format(sampleRate);
    SourceDataLine open = null;
    try {
      open = lineFactory.getLine(format);
      open.open(format);
      applyVolume(open, volumePercent);
      open.start();
      this.line = open;

      int offset = 0;
      while (offset < pcm.length) {
        if (generation.get() != gen) {
          break;
        }
        int len = Math.min(CHUNK_BYTES, pcm.length - offset);
        // write() blocks until the line can accept more, pacing playback to real time.
        offset += open.write(pcm, offset, len);
      }
      if (generation.get() == gen) {
        open.drain();
      }
    } catch (Exception e) {
      log.warn("Audio playback failed: {}", e.getMessage());
    } finally {
      if (open != null) {
        try {
          open.stop();
          open.close();
        } catch (Exception ignored) {
          // best-effort line teardown
        }
      }
      if (this.line == open) {
        this.line = null;
      }
    }
  }

  @Override
  public AudioStream beginStream(int volumePercent) {
    // Share the buffered path's generation counter, so a new line or stop() interrupts a streamed
    // line and vice versa. The line itself is opened lazily on the first chunk (see LineStream).
    long gen = generation.incrementAndGet();
    return new LineStream(gen, volumePercent);
  }

  @Override
  public void stop() {
    // Invalidate the current generation so the streaming loop bails, then unblock any pending
    // write() by flushing and stopping the line.
    generation.incrementAndGet();
    SourceDataLine current = this.line;
    if (current != null) {
      try {
        current.stop();
        current.flush();
      } catch (Exception ignored) {
        // best-effort interruption
      }
    }
  }

  @Override
  public void close() {
    stop();
  }

  /**
   * A streamed line: chunks are written as they arrive and the {@link SourceDataLine} is opened
   * lazily on the first non-empty chunk. Guarded by the shared {@link #generation} counter, so
   * {@link #stop()} or a newer line makes {@link #write} drop its chunk and release the line at
   * once, while the producer keeps handing over (now ignored) chunks so it can finish draining its
   * source. Not thread-safe: one synthesis worker drives one instance.
   */
  private final class LineStream implements AudioStream {
    private final long gen;
    private final int volumePercent;
    private SourceDataLine sdl;
    private boolean released;

    private LineStream(long gen, int volumePercent) {
      this.gen = gen;
      this.volumePercent = volumePercent;
    }

    @Override
    public void write(float[] samples, int sampleRate) {
      if (released) {
        return;
      }
      if (generation.get() != gen) {
        release();
        return;
      }
      if (samples == null || samples.length == 0) {
        return;
      }
      try {
        if (sdl == null) {
          AudioFormat format = PcmAudio.format(sampleRate);
          SourceDataLine open = lineFactory.getLine(format);
          open.open(format);
          applyVolume(open, volumePercent);
          open.start();
          sdl = open;
          line = open; // publish so stop() can flush the active line
        }
        byte[] pcm = PcmAudio.toPcm16LE(samples);
        int offset = 0;
        while (offset < pcm.length) {
          if (generation.get() != gen) {
            release();
            return;
          }
          offset += sdl.write(pcm, offset, Math.min(CHUNK_BYTES, pcm.length - offset));
        }
      } catch (Exception e) {
        log.warn("Audio streaming failed: {}", e.getMessage());
        release();
      }
    }

    @Override
    public void end() {
      try {
        if (sdl != null && !released && generation.get() == gen) {
          sdl.drain();
        }
      } catch (Exception ignored) {
        // best-effort drain
      } finally {
        release();
      }
    }

    private void release() {
      released = true;
      SourceDataLine current = sdl;
      sdl = null;
      if (current == null) {
        return;
      }
      try {
        current.stop();
        current.close();
      } catch (Exception ignored) {
        // best-effort teardown
      }
      if (line == current) {
        line = null;
      }
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
