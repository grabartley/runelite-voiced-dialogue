package com.grahambartley.synthesis;

import com.grahambartley.tts.Pcm;
import lombok.extern.slf4j.Slf4j;

/**
 * Single point of truth for the active {@link SynthesisBackend} and how emotion is downgraded.
 *
 * <p>The plugin is Cloud-only: there is one backend (OpenRouter), which this simply provides. A
 * line the backend cannot voice (for example when no API key is set) is left unvoiced rather than
 * routed elsewhere.
 *
 * <p>The emotion-downgrade rule lives here and nowhere else: {@link #synthesize} rewrites a
 * request's emotion to {@link Emotion#NEUTRAL} whenever the backend does not list it in {@link
 * SynthesisBackend#supportedEmotions()}, so the backend never has to special-case an emotion it
 * cannot voice.
 */
@Slf4j
public final class BackendProvider {

  private final SynthesisBackend backend;

  public BackendProvider(SynthesisBackend backend) {
    this.backend = backend;
  }

  /** The active synthesis backend. */
  public SynthesisBackend active() {
    return backend;
  }

  /**
   * Applies the emotion-downgrade rule for a backend: if the backend cannot voice the request's
   * emotion, the emotion is rewritten to {@link Emotion#NEUTRAL}. This is the single definition of
   * the rule, shared by {@link #synthesize} and the pipeline's cache-key computation.
   */
  public static SynthesisRequest downgradeFor(SynthesisBackend backend, SynthesisRequest request) {
    if (backend.supportedEmotions().contains(request.emotion())) {
      return request;
    }
    return request.withEmotion(Emotion.NEUTRAL);
  }

  /**
   * Convenience entry that resolves {@link #active()} and synthesizes in one call, applying the
   * emotion-downgrade rule first so the backend only ever receives an emotion it supports. Returns
   * {@code null} on failure. Use this only when cache-key parity does not matter (e.g. tests); the
   * pipeline instead resolves {@link #active()} itself and calls {@link #synthesizeWith} so the
   * backend reflected in the cache key is the one that actually runs.
   */
  public Pcm synthesize(SynthesisRequest request) {
    return backend.synthesize(downgradeFor(backend, request));
  }

  /**
   * Synthesizes through a specific, already-resolved backend. Used by the pipeline so the backend
   * chosen when a line is enqueued matches the backend reflected in its cache key.
   */
  public Pcm synthesizeWith(SynthesisBackend backend, SynthesisRequest request) {
    return backend.synthesize(downgradeFor(backend, request));
  }

  /**
   * Warms up the backend on the pipeline thread, so it does its off-thread handshake before the
   * first line and the game thread never blocks on it. Safe to call repeatedly: {@code warmUp} is
   * idempotent.
   */
  public void warmUpActive() {
    backend.warmUp();
  }

  /** Releases the backend. */
  public void close() {
    try {
      backend.close();
    } catch (RuntimeException e) {
      log.debug("Error closing backend {}: {}", backend.id(), e.getMessage());
    }
  }
}
