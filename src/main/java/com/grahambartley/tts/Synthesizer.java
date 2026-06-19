package com.grahambartley.tts;

/**
 * Turns text into PCM audio for a given speaker.
 *
 * <p>Kept as a seam so the dialogue pipeline can be unit tested with a fake synth instead of the
 * heavy native Kokoro engine.
 */
public interface Synthesizer {

  /**
   * Synthesizes {@code text} for {@code speakerId}, or returns {@code null} if synthesis failed.
   */
  Pcm synthesize(String text, int speakerId);
}
