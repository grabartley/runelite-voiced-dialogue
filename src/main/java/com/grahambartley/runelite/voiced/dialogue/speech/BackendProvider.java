package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.PcmSink;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Single point of truth for the active {@link SynthesisBackend} and how emotion is downgraded.
 *
 * <p>The plugin is Cloud-only, with one backend per {@link VoicedDialogueConfig.TtsProvider}:
 * OpenRouter and Google AI Studio. {@link #active()} resolves the configured provider's backend
 * live on every call, so switching providers takes effect on the next line with no restart. A line
 * the active backend cannot voice (for example when no API key is set) is left unvoiced rather than
 * routed to the other provider.
 *
 * <p>The emotion-downgrade rule lives here and nowhere else: {@link #synthesize} rewrites a
 * request's emotion to {@link Emotion#NEUTRAL} whenever the backend does not list it in {@link
 * SynthesisBackend#supportedEmotions()}, so the backend never has to special-case an emotion it
 * cannot voice.
 */
@Slf4j
public final class BackendProvider {

  private final SynthesisBackend openRouter;
  private final SynthesisBackend googleAiStudio;
  private final Supplier<VoicedDialogueConfig.TtsProvider> selectedProvider;

  /** A provider fixed to one backend, for call sites and tests with no provider choice. */
  public BackendProvider(SynthesisBackend backend) {
    this(backend, backend, () -> VoicedDialogueConfig.TtsProvider.OPENROUTER);
  }

  public BackendProvider(
      SynthesisBackend openRouter,
      SynthesisBackend googleAiStudio,
      Supplier<VoicedDialogueConfig.TtsProvider> selectedProvider) {
    this.openRouter = openRouter;
    this.googleAiStudio = googleAiStudio;
    this.selectedProvider = selectedProvider;
  }

  /**
   * Drops every backend's rate-limit back-off. Called when a key or the provider changes: a window
   * says the provider refused the credentials it was holding at the time, so it is evidence about
   * nothing once they change, on either backend.
   */
  public void clearRateLimits() {
    openRouter.clearRateLimit();
    googleAiStudio.clearRateLimit();
  }

  /** The configured provider's backend, resolved live so a provider switch needs no restart. */
  public SynthesisBackend active() {
    return selectedProvider.get() == VoicedDialogueConfig.TtsProvider.GOOGLE_AI_STUDIO
        ? googleAiStudio
        : openRouter;
  }

  /**
   * Applies the emotion-downgrade rule for a backend: if the backend cannot voice the request's
   * emotion, the emotion is rewritten to {@link Emotion#NEUTRAL}. This is the single definition of
   * the rule, shared by {@link #synthesizeWith} and the pipeline's cache-key computation.
   */
  public static SynthesisRequest downgradeFor(SynthesisBackend backend, SynthesisRequest request) {
    if (backend.supportedEmotions().contains(request.emotion())) {
      return request;
    }
    return request.withEmotion(Emotion.NEUTRAL);
  }

  /**
   * Synthesizes through a specific, already-resolved backend. Used by the pipeline so the backend
   * chosen when a line is enqueued matches the backend reflected in its cache key.
   */
  public Pcm synthesizeWith(SynthesisBackend backend, SynthesisRequest request) {
    return backend.synthesize(downgradeFor(backend, request));
  }

  /**
   * Streams synthesis through a specific, already-resolved backend, feeding decoded chunks to
   * {@code sink} as they arrive and returning the complete Pcm for caching. Mirrors {@link
   * #synthesizeWith} (applying the same emotion downgrade) so the backend that runs matches the one
   * in the cache key.
   */
  public Pcm synthesizeStreamingWith(
      SynthesisBackend backend, SynthesisRequest request, PcmSink sink) {
    return backend.synthesizeStreaming(downgradeFor(backend, request), sink);
  }

  /**
   * Warms up the backend on the pipeline thread, so it does its off-thread handshake before the
   * first line and the game thread never blocks on it. Safe to call repeatedly: {@code warmUp} is
   * idempotent.
   */
  public void warmUpActive() {
    active().warmUp();
  }

  /** Releases both provider backends (each once, even when fixed to a single backend). */
  public void close() {
    closeQuietly(openRouter);
    if (googleAiStudio != openRouter) {
      closeQuietly(googleAiStudio);
    }
  }

  private static void closeQuietly(SynthesisBackend backend) {
    try {
      backend.close();
    } catch (RuntimeException e) {
      log.debug("Error closing backend {}: {}", backend.id(), e.getMessage());
    }
  }
}
