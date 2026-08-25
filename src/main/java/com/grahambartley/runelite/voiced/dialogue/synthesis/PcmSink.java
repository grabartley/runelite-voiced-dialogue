package com.grahambartley.runelite.voiced.dialogue.synthesis;

/**
 * Receives decoded PCM chunks from a streaming synthesis as they are produced, so playback can
 * start before the whole line has downloaded. Called synchronously by the single worker reading the
 * response body, so an implementation need not be thread-safe.
 */
@FunctionalInterface
public interface PcmSink {

  /** Accepts the next chunk of mono float samples in [-1, 1] at {@code sampleRate}. */
  void accept(float[] samples, int sampleRate);
}
