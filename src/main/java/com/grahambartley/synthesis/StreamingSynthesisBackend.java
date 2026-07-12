package com.grahambartley.synthesis;

import com.grahambartley.tts.Pcm;
import java.util.function.Consumer;

/** A backend that can emit playable PCM before synthesis of the complete line has finished. */
public interface StreamingSynthesisBackend extends SynthesisBackend {

  /** Whether incremental synthesis is currently enabled for live lines. */
  boolean streamingEnabled();

  /**
   * Synthesizes a line, passing each PCM chunk to {@code onChunk} as it arrives and returning the
   * complete PCM for caching. Returns {@code null} when the stream did not complete cleanly.
   */
  Pcm synthesizeStreaming(SynthesisRequest request, Consumer<Pcm> onChunk);
}
