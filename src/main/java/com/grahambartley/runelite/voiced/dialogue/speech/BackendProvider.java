package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.PcmSink;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class BackendProvider {

  private final SynthesisBackend openRouter;
  private final SynthesisBackend googleAiStudio;
  private final Supplier<VoicedDialogueConfig.TtsProvider> selectedProvider;

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

  public void clearRateLimits() {
    openRouter.clearRateLimit();
    googleAiStudio.clearRateLimit();
  }

  public SynthesisBackend active() {
    return selectedProvider.get() == VoicedDialogueConfig.TtsProvider.GOOGLE_AI_STUDIO
        ? googleAiStudio
        : openRouter;
  }

  public static SynthesisRequest downgradeFor(SynthesisBackend backend, SynthesisRequest request) {
    if (backend.supportedEmotions().contains(request.emotion())) {
      return request;
    }
    return request.withEmotion(Emotion.NEUTRAL);
  }

  public Pcm synthesizeWith(SynthesisBackend backend, SynthesisRequest request) {
    return backend.synthesize(downgradeFor(backend, request));
  }

  public Pcm synthesizeStreamingWith(
      SynthesisBackend backend, SynthesisRequest request, PcmSink sink) {
    return backend.synthesizeStreaming(downgradeFor(backend, request), sink);
  }

  public void warmUpActive() {
    active().warmUp();
  }

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
