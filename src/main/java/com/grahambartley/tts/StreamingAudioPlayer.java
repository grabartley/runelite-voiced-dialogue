package com.grahambartley.tts;

import java.util.concurrent.LinkedBlockingQueue;
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
  private volatile SourceLineSession session;
  private long latestStreamId = Long.MIN_VALUE;

  public StreamingAudioPlayer() {
    this(AudioSystem::getSourceDataLine);
  }

  StreamingAudioPlayer(LineFactory lineFactory) {
    this.lineFactory = lineFactory;
  }

  @Override
  public void stream(float[] samples, int sampleRate, int volumePercent) {
    streamInternal(null, samples, sampleRate, volumePercent);
  }

  @Override
  public void stream(long streamId, float[] samples, int sampleRate, int volumePercent) {
    streamInternal(streamId, samples, sampleRate, volumePercent);
  }

  private void streamInternal(Long streamId, float[] samples, int sampleRate, int volumePercent) {
    if (samples == null || samples.length == 0) {
      return;
    }
    byte[] pcm = PcmAudio.toPcm16LE(samples);
    AudioFormat format = PcmAudio.format(sampleRate);
    SourceDataLine open = null;
    long gen;
    try {
      synchronized (this) {
        if (streamId != null && streamId < latestStreamId) {
          return;
        }
        if (streamId != null) {
          latestStreamId = streamId;
        }
        gen = generation.incrementAndGet();
        open = lineFactory.getLine(format);
        open.open(format);
        applyVolume(open, volumePercent);
        open.start();
        this.line = open;
      }

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
      synchronized (this) {
        if (this.line == open) {
          this.line = null;
        }
      }
    }
  }

  @Override
  public synchronized void advance(long streamId) {
    latestStreamId = Math.max(latestStreamId, streamId);
  }

  @Override
  public synchronized StreamSession openStream(long streamId, int sampleRate, int volumePercent) {
    if (streamId < latestStreamId) {
      return null;
    }
    if (session != null) {
      session.abort();
    }
    latestStreamId = streamId;
    long gen = generation.incrementAndGet();
    AudioFormat format = PcmAudio.format(sampleRate);
    SourceDataLine open = null;
    try {
      open = lineFactory.getLine(format);
      open.open(format);
      applyVolume(open, volumePercent);
      open.start();
      line = open;
      SourceLineSession created = new SourceLineSession(open, gen);
      session = created;
      return created;
    } catch (Exception e) {
      log.warn("Audio playback failed: {}", e.getMessage());
      if (open != null) {
        try {
          open.close();
        } catch (Exception ignored) {
          // Best-effort setup failure cleanup.
        }
      }
      return null;
    }
  }

  private final class SourceLineSession implements StreamSession {
    private final byte[] end = new byte[0];
    private final SourceDataLine open;
    private final long gen;
    private final LinkedBlockingQueue<byte[]> chunks = new LinkedBlockingQueue<>();
    private volatile boolean closed;
    private volatile boolean finishing;

    private SourceLineSession(SourceDataLine open, long gen) {
      this.open = open;
      this.gen = gen;
      Thread writer = new Thread(this::writeChunks, "tts-audio-stream");
      writer.setDaemon(true);
      writer.start();
    }

    @Override
    public void write(float[] samples) {
      if (closed
          || finishing
          || samples == null
          || samples.length == 0
          || generation.get() != gen) {
        return;
      }
      chunks.offer(PcmAudio.toPcm16LE(samples));
    }

    @Override
    public void finish() {
      if (closed || finishing) {
        return;
      }
      finishing = true;
      chunks.offer(end);
    }

    @Override
    public void abort() {
      if (closed) {
        return;
      }
      closed = true;
      chunks.clear();
      chunks.offer(end);
      closeLine(true);
    }

    private void writeChunks() {
      boolean finished = false;
      try {
        while (!closed) {
          byte[] pcm = chunks.take();
          if (pcm == end) {
            finished = true;
            break;
          }
          int offset = 0;
          while (offset < pcm.length && generation.get() == gen) {
            int length = Math.min(CHUNK_BYTES, pcm.length - offset);
            offset += open.write(pcm, offset, length);
          }
        }
      } catch (InterruptedException e) {
        log.debug("Incremental audio writer interrupted");
      } catch (RuntimeException e) {
        log.debug("Incremental audio writer stopped: {}", e.getMessage());
      } finally {
        if (finished && !closed && generation.get() == gen) {
          try {
            open.drain();
          } catch (Exception ignored) {
            // Interruption can close the line while drain is blocked.
          }
        }
        if (!closed) {
          closeLine(false);
        }
      }
    }

    private void closeLine(boolean flush) {
      closed = true;
      try {
        if (flush) {
          open.flush();
        }
        open.stop();
        open.close();
      } catch (Exception ignored) {
        // Best-effort line teardown.
      } finally {
        synchronized (StreamingAudioPlayer.this) {
          if (line == open) {
            line = null;
          }
          if (session == this) {
            session = null;
          }
        }
      }
    }
  }

  @Override
  public synchronized void stop() {
    // Invalidate the current generation so the streaming loop bails, then unblock any pending
    // write() by flushing and stopping the line.
    generation.incrementAndGet();
    SourceLineSession currentSession = session;
    if (currentSession != null) {
      currentSession.abort();
      return;
    }
    SourceDataLine current = this.line;
    if (current != null) {
      try {
        current.stop();
        current.flush();
        current.close();
      } catch (Exception ignored) {
        // best-effort interruption
      } finally {
        if (this.line == current) {
          this.line = null;
        }
      }
    }
  }

  @Override
  public void close() {
    stop();
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
