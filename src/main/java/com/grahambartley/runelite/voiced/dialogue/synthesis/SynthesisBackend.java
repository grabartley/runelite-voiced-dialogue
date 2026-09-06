package com.grahambartley.runelite.voiced.dialogue.synthesis;

import com.grahambartley.runelite.voiced.dialogue.tts.Pcm;
import java.util.EnumSet;

/**
 * A single text-to-speech engine the plugin can synthesize through.
 *
 * <p>Every synthesis flow goes through a backend. {@link BackendProvider} owns the backend and the
 * emotion-downgrade rule, so an implementation only has to render the emotions it advertises in
 * {@link #supportedEmotions()}; it never receives an unsupported emotion.
 */
public interface SynthesisBackend {

  /** Stable identifier, e.g. {@code "cloud-openrouter"}. */
  String id();

  /** Whether this backend can actually run right now (e.g. an API key is set). */
  boolean isAvailable();

  /**
   * The user-facing notice shown when this backend is unavailable for lack of an API key, naming
   * the provider and where to get a key so the fix is actionable. Shared between the backend's own
   * one-time warning and the plugin's session start-up check so the two paths never drift.
   */
  default String missingKeyNotice() {
    return "Add your API key in the Voiced Dialogue settings to hear dialogue; without a key,"
        + " lines are not voiced.";
  }

  /** The emotions this backend can voice. Requests outside this set are downgraded to neutral. */
  EnumSet<Emotion> supportedEmotions();

  /** Synthesizes the request to PCM, or returns {@code null} on failure. */
  Pcm synthesize(SynthesisRequest request);

  /**
   * Synthesizes the request while delivering decoded audio to {@code sink} in chunks as it is
   * produced, and returns the complete {@link Pcm} for caching (or {@code null} when the line
   * failed or is too incomplete to cache; it may still have played through the sink).
   *
   * <p>Default: the buffered behavior. Synthesize the whole line, then hand it to the sink as a
   * single chunk, so a backend that cannot stream still works through the streaming call site. A
   * backend that can stream overrides this to start feeding the sink as bytes arrive.
   */
  default Pcm synthesizeStreaming(SynthesisRequest request, PcmSink sink) {
    Pcm pcm = synthesize(request);
    if (pcm != null) {
      sink.accept(pcm.getSamples(), pcm.getSampleRate());
    }
    return pcm;
  }

  /**
   * An extra cache-key fragment that distinguishes audio this backend would render differently for
   * the same {@code (voice, emotion, text)} because of backend-specific state outside the request.
   *
   * <p>Default {@code ""}: most backends render a request the same way every time, so the standard
   * {@code (backendId, voiceKey, emotion, text)} identity is enough. A backend whose output depends
   * on state outside the request (for example a selectable model or voice) overrides this to fold
   * that state in, so two renderings never collide in the cache. Must be a stable, filesystem-safe
   * fragment for a given backend state; return {@code ""} when there is no variant.
   */
  default String cacheVariant(SynthesisRequest request) {
    return "";
  }

  /**
   * Whether this backend is currently backing off after being rate-limited (HTTP 429), so
   * speculative work (prefetch) should hold off rather than pile onto the limit. User-initiated
   * lines ignore this and always attempt synthesis. Default {@code false}: a backend with no remote
   * rate limit is never throttled.
   */
  default boolean isThrottled() {
    return false;
  }

  /**
   * Optional one-off warm-up (e.g. a connection handshake) run on the pipeline thread before first
   * use.
   */
  default void warmUp() {}

  /** Optional resource release when the backend is torn down. */
  default void close() {}
}
