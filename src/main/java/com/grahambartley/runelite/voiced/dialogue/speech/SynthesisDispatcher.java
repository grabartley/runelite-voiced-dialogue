package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.CaveEchoPolicy;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.EmotionResolver;
import com.grahambartley.runelite.voiced.dialogue.profile.ResolvedSpeaker;
import com.grahambartley.runelite.voiced.dialogue.profile.Speaker;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceManager;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceTraceFormatter;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;

@Slf4j
public final class SynthesisDispatcher {

  static final int FOLLOWER_SPEAKER_ID = -1;

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

  public void speakFollower(String text, NpcGender gender) {
    speakOverhead(
        text,
        () -> voiceManager.resolveFollower(gender),
        null,
        FOLLOWER_SPEAKER_ID,
        config::volume);
  }

  public void speakAmbient(String text, NPC npc, IntSupplier distanceVolume) {
    speakOverhead(
        text, () -> voiceManager.resolveNpc(npc), npc.getName(), npc.getIndex(), distanceVolume);
  }

  private void speakOverhead(
      String text,
      Supplier<ResolvedSpeaker> speaker,
      String traceName,
      int speakerId,
      IntSupplier lineVolume) {
    SynthesisBackend backend = backendProvider.active();
    if (!backend.isAvailable() || backend.isThrottled()) {
      return;
    }
    ResolvedSpeaker resolved = speaker.get();
    SynthesisRequest request =
        new SynthesisRequest(
            text, resolved.voice(), Emotion.NEUTRAL, resolved.profile(), false, false);
    trace(backend, request, traceName);
    audioService.speakAmbient(request, echoFor(request), speakerId, lineVolume);
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
