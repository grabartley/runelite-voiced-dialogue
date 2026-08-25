package com.grahambartley.runelite.voiced.dialogue.tts;

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
    // line and vice versa. The producer (a synthesis worker) hands chunks to write() as it reads
    // the
    // body at network speed; a dedicated player thread drains them to the line in real time, so the
    // worker is freed as soon as the body is read rather than held for the whole spoken duration.
    long gen = generation.incrementAndGet();
    return new BufferedLineStream(gen, volumePercent);
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
   * A streamed line that decouples the network read from playback. {@link #write} (called by the
   * synthesis worker as it reads the body) enqueues chunks without blocking, so the worker finishes
   * at network speed and is freed rather than held for the whole spoken duration; a dedicated
   * daemon player thread drains the queue and paces it to the {@link SourceDataLine} in real time.
   * The shared {@link #generation} counter interrupts it: a newer line or {@link #stop()} makes the
   * player bail (no drain) and close the line, and makes {@link #write} drop further chunks so the
   * worker can keep draining its source cheaply. The producer drives one instance from one thread.
   */
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
      // Drop superseded/empty chunks (and never start a player for them) so the producer, after a
      // skip, keeps draining the body cheaply for the cache without pacing to a line no one hears.
      if (samples == null || samples.length == 0 || generation.get() != gen) {
        return;
      }
      this.sampleRate = sampleRate;
      queue.offer(samples);
      startPlayer();
    }

    @Override
    public void end() {
      // Signal end-of-stream and return promptly: the producer must not block on playback. The
      // player thread drains the tail and closes the line itself. A no-op if nothing ever played.
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
        AudioFormat format = PcmAudio.format(sampleRate);
        sdl = lineFactory.getLine(format);
        sdl.open(format);
        applyVolume(sdl, volumePercent);
        sdl.start();
        line = sdl; // publish so stop() can flush the active line
        while (generation.get() == gen) {
          float[] chunk = queue.poll(20, TimeUnit.MILLISECONDS);
          if (chunk == null) {
            if (ended && queue.isEmpty()) {
              break; // producer finished and the buffer is drained
            }
            continue; // waiting on the next chunk
          }
          byte[] pcm = PcmAudio.toPcm16LE(chunk);
          int offset = 0;
          while (offset < pcm.length && generation.get() == gen) {
            offset += sdl.write(pcm, offset, Math.min(CHUNK_BYTES, pcm.length - offset));
          }
        }
        if (generation.get() == gen) {
          sdl.drain();
        }
      } catch (Exception e) {
        log.warn("Audio streaming failed: {}", e.getMessage());
      } finally {
        if (sdl != null) {
          try {
            sdl.stop();
            sdl.close();
          } catch (Exception ignored) {
            // best-effort teardown
          }
        }
        if (line == sdl) {
          line = null;
        }
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
