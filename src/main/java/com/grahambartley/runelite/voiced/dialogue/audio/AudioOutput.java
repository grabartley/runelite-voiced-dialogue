package com.grahambartley.runelite.voiced.dialogue.audio;

/**
 * Plays back PCM audio, one line at a time, with the ability to interrupt the current line.
 *
 * <p>Kept as a seam so the dialogue pipeline can be unit tested without touching real audio
 * hardware.
 */
public interface AudioOutput {

  /**
   * Streams the given mono float samples at {@code sampleRate} and {@code volumePercent} (0-100),
   * blocking the calling thread until playback finishes or is interrupted via {@link #stop()}.
   */
  void stream(float[] samples, int sampleRate, int volumePercent);

  /**
   * Begins a streamed line whose audio arrives in chunks over time (see {@link AudioStream}), so
   * playback can start on the first chunk instead of waiting for the whole clip to synthesize. The
   * caller writes chunks to the returned handle as they are produced and calls {@link
   * AudioStream#end()} when the source is exhausted. Like {@link #stream}, a newer line or {@link
   * #stop()} supersedes this one: subsequent writes are dropped and the line is released.
   */
  AudioStream beginStream(int volumePercent);

  /** Interrupts the line currently playing (if any) so {@link #stream} returns promptly. */
  void stop();

  /** Releases any held audio resources. */
  void close();

  /**
   * A single in-progress streamed line. Driven by exactly one synthesis worker (not thread-safe),
   * while {@link AudioOutput#stop()} may supersede it concurrently from the game thread.
   */
  interface AudioStream {

    /**
     * Appends the next chunk of mono float samples at {@code sampleRate}, pacing playback to real
     * time. Opens the underlying line lazily on the first non-empty chunk. Once the line has been
     * superseded (a newer line or {@link AudioOutput#stop()}), this drops the chunk and releases
     * the line, so the producer can keep draining its source cheaply without playing it.
     */
    void write(float[] samples, int sampleRate);

    /**
     * Finishes the line: drains any remaining buffered audio if still current, then releases it.
     */
    void end();
  }
}
