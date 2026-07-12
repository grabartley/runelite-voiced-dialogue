package com.grahambartley.tts;

/**
 * Plays back PCM audio, one line at a time, with the ability to interrupt the current line.
 *
 * <p>Kept as a seam so the dialogue pipeline can be unit tested without touching real audio
 * hardware.
 */
public interface AudioOutput {

  /** One incrementally written line of PCM audio. */
  interface StreamSession {
    /** Queues the next mono sample chunk without blocking the synthesis worker. */
    void write(float[] samples);

    /** Drains the completed line and releases its audio resources. */
    void finish();

    /** Releases resources without draining, used after interruption. */
    void abort();
  }

  /**
   * Streams the given mono float samples at {@code sampleRate} and {@code volumePercent} (0-100),
   * blocking the calling thread until playback finishes or is interrupted via {@link #stop()}.
   */
  void stream(float[] samples, int sampleRate, int volumePercent);

  /** Streams a complete buffer only if its dialogue generation is still current. */
  default void stream(long streamId, float[] samples, int sampleRate, int volumePercent) {
    stream(samples, sampleRate, volumePercent);
  }

  /**
   * Opens an incremental stream, or returns {@code null} when only complete buffers are supported.
   */
  default StreamSession openStream(long streamId, int sampleRate, int volumePercent) {
    return null;
  }

  /**
   * Marks the newest dialogue generation so delayed older streams can be rejected before opening.
   */
  default void advance(long streamId) {}

  /** Interrupts the line currently playing (if any) so {@link #stream} returns promptly. */
  void stop();

  /** Releases any held audio resources. */
  void close();
}
