package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.CaveEchoPolicy;
import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.EmotionResolver;
import com.grahambartley.runelite.voiced.dialogue.profile.ResolvedSpeaker;
import com.grahambartley.runelite.voiced.dialogue.profile.Speaker;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceManager;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceTraceFormatter;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds {@link SynthesisRequest}s for dialogue, narration, and public-chat lines and hands them to
 * the off-thread synth + playback pipeline. Every speak path shares the same availability guard,
 * speaker resolution, emotion resolution, and cave-echo gate, so this is the single place a line
 * becomes a request. Never blocks the game thread.
 */
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

  /**
   * Speaks a dialogue line. The caller passes the speaker's chat-head expression animation id (or
   * {@link com.grahambartley.runelite.voiced.dialogue.capture.DialogueWidgetReader#NO_EXPRESSION}
   * when there is no head); it is resolved to an {@link Emotion} and ridden into the request.
   */
  public void speakDialogue(String text, Speaker speaker, String npcName, int headAnimationId) {
    Emotion emotion = emotionResolver.resolve(headAnimationId, config.cloudEmotion());
    if (config.debugMode()) {
      log.info("[TTS voice] resolved emotion {} for head animation {}", emotion, headAnimationId);
    }
    ResolvedSpeaker resolved = voiceManager.resolve(speaker, npcName);
    boolean player = speaker == Speaker.PLAYER;
    dispatch(
        new SynthesisRequest(
            text,
            resolved.voice(),
            emotion,
            resolved.profile(),
            /* skipTranslation= */ false,
            player),
        npcName);
  }

  /**
   * Voices the player's own public chat through the same player voice path as their dialogue lines,
   * but always neutral (public chat has no chat-head) and with translation/global-quirk bypassed,
   * so chat is spoken exactly as typed.
   */
  public void speakPublicChat(String text) {
    ResolvedSpeaker resolved = voiceManager.resolve(Speaker.PLAYER, null);
    dispatch(
        new SynthesisRequest(
            text,
            resolved.voice(),
            Emotion.NEUTRAL,
            resolved.profile(),
            /* skipTranslation= */ true,
            /* player= */ true),
        null);
  }

  /**
   * Speaks a narration box (item, double-item, or plain message) in the game's own narrator voice.
   * Always neutral, since a narration box carries no chat head, and voiced from one fixed spec and
   * profile so the narrator sounds the same in every session. Translation applies as it does to
   * dialogue; the Player and NPC speaking styles do not, because narration is the game speaking
   * rather than a character.
   */
  public void speakNarration(String text) {
    ResolvedSpeaker resolved = voiceManager.resolveNarrator();
    dispatch(
        new SynthesisRequest(
            text,
            resolved.voice(),
            Emotion.NEUTRAL,
            resolved.profile(),
            /* skipTranslation= */ false,
            /* player= */ false),
        null);
  }

  /**
   * Hands a built request to the off-thread synth pipeline, guarded by the availability check every
   * speak path needs: no-op when the active backend is unavailable. On dispatch (debug mode) it
   * emits one consolidated {@code [TTS line]} record of the whole resolved decision, so a single
   * grep gives the backend, emotion, and the full voice metadata used for synthesis.
   */
  private void dispatch(SynthesisRequest request, String npcName) {
    SynthesisBackend backend = backendProvider.active();
    if (!backend.isAvailable()) {
      return;
    }
    if (config.debugMode()) {
      // The effective emotion is what the backend will actually voice after the downgrade rule, so
      // the record reflects the real decision.
      Emotion effective = BackendProvider.downgradeFor(backend, request).emotion();
      CharacterProfile profile = request.profile();
      log.info(
          VoiceTraceFormatter.buildResolvedLine(
              backend.id(),
              request.player(),
              npcName,
              effective.name(),
              request.voice().race(),
              request.voice().gender(),
              request.voice().child(),
              request.voice().voiceSeed(),
              profile == null ? null : profile.name(),
              profile == null ? null : profile.accent()));
    }
    audioService.speak(request, caveEchoPolicy.shouldEcho());
  }
}
