package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.CaveEchoPolicy;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.EmotionResolver;
import com.grahambartley.runelite.voiced.dialogue.profile.ResolvedSpeaker;
import com.grahambartley.runelite.voiced.dialogue.profile.Speaker;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceManager;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceTraceFormatter;
import java.util.function.IntSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;

@Slf4j
public final class SynthesisDispatcher {

  private final VoiceManager voiceManager;
  private final EmotionResolver emotionResolver;
  private final CaveEchoPolicy caveEchoPolicy;
  private final VoicedDialogueConfig config;
  private final BackendProvider backendProvider;
  private final DialogueAudioService audioService;

  public SynthesisDispatcher(
      VoiceManager voiceManager,
      EmotionResolver emotionResolver,
      CaveEchoPolicy caveEchoPolicy,
      VoicedDialogueConfig config,
      BackendProvider backendProvider,
      DialogueAudioService audioService) {
    this.voiceManager = voiceManager;
    this.emotionResolver = emotionResolver;
    this.caveEchoPolicy = caveEchoPolicy;
    this.config = config;
    this.backendProvider = backendProvider;
    this.audioService = audioService;
  }

  public void speakDialogue(String text, Speaker speaker, String npcName, int headAnimationId) {
    Emotion emotion = emotionResolver.resolve(headAnimationId, config.cloudEmotion());
    if (config.debugMode()) {
      log.info("[TTS voice] resolved emotion {} for head animation {}", emotion, headAnimationId);
    }
    ResolvedSpeaker resolved = voiceManager.resolve(speaker, npcName);
    boolean player = speaker == Speaker.PLAYER;
    dispatch(
        new SynthesisRequest(text, resolved.voice(), emotion, resolved.profile(), false, player),
        npcName);
  }

  public void speakPublicChat(String text) {
    ResolvedSpeaker resolved = voiceManager.resolve(Speaker.PLAYER, null);
    dispatch(
        new SynthesisRequest(
            text, resolved.voice(), Emotion.NEUTRAL, resolved.profile(), true, true),
        null);
  }

  public void speakAmbient(String text, NPC npc, IntSupplier distanceVolume) {
    SynthesisBackend backend = backendProvider.active();
    if (!backend.isAvailable() || backend.isThrottled()) {
      return;
    }
    ResolvedSpeaker resolved = voiceManager.resolveNpc(npc);
    SynthesisRequest request =
        new SynthesisRequest(
            text, resolved.voice(), Emotion.NEUTRAL, resolved.profile(), false, false);
    trace(backend, request, npc.getName());
    audioService.speakAmbient(request, echoFor(request), distanceVolume);
  }

  public void speakNarration(String text) {
    ResolvedSpeaker resolved = voiceManager.resolveNarrator();
    dispatch(
        new SynthesisRequest(
            text, resolved.voice(), Emotion.NEUTRAL, resolved.profile(), false, false),
        null);
  }

  private void dispatch(SynthesisRequest request, String npcName) {
    SynthesisBackend backend = backendProvider.active();
    if (!backend.isAvailable()) {
      return;
    }
    trace(backend, request, npcName);
    audioService.speak(request, echoFor(request));
  }

  private boolean echoFor(SynthesisRequest request) {
    return !request.voice().narrator() && caveEchoPolicy.shouldEcho();
  }

  private void trace(SynthesisBackend backend, SynthesisRequest request, String npcName) {
    if (!config.debugMode()) {
      return;
    }
    Emotion effective = BackendProvider.downgradeFor(backend, request).emotion();
    log.info(
        VoiceTraceFormatter.buildResolvedLine(
            backend.id(), request.voice(), npcName, effective.name(), request.profile()));
  }
}
