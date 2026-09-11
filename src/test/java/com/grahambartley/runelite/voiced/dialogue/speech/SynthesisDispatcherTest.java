package com.grahambartley.runelite.voiced.dialogue.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.CaveEchoPolicy;
import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.EmotionResolver;
import com.grahambartley.runelite.voiced.dialogue.profile.ResolvedSpeaker;
import com.grahambartley.runelite.voiced.dialogue.profile.Speaker;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceManager;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class SynthesisDispatcherTest {

  private final VoiceManager voiceManager = mock(VoiceManager.class);
  private final EmotionResolver emotionResolver = mock(EmotionResolver.class);
  private final CaveEchoPolicy caveEchoPolicy = mock(CaveEchoPolicy.class);
  private final VoicedDialogueConfig config = mock(VoicedDialogueConfig.class);
  private final BackendProvider backendProvider = mock(BackendProvider.class);
  private final SynthesisBackend backend = mock(SynthesisBackend.class);
  private final DialogueAudioService audioService = mock(DialogueAudioService.class);

  private final SynthesisDispatcher dispatcher =
      new SynthesisDispatcher(
          voiceManager, emotionResolver, caveEchoPolicy, config, backendProvider, audioService);

  @Before
  public void setUp() {
    when(backendProvider.active()).thenReturn(backend);
  }

  @Test
  public void dialogueLineBuildsAndDispatchesTheRequestWithTheEchoFlag() {
    when(backend.isAvailable()).thenReturn(true);
    when(config.cloudEmotion()).thenReturn(true);
    VoiceSpec spec = mock(VoiceSpec.class);
    CharacterProfile profile = mock(CharacterProfile.class);
    when(voiceManager.resolve(Speaker.NPC, "Bob")).thenReturn(new ResolvedSpeaker(spec, profile));
    when(emotionResolver.resolve(614, true)).thenReturn(Emotion.ANGRY);
    when(caveEchoPolicy.shouldEcho()).thenReturn(true);

    dispatcher.speakDialogue("Grr!", Speaker.NPC, "Bob", 614);

    ArgumentCaptor<SynthesisRequest> req = ArgumentCaptor.forClass(SynthesisRequest.class);
    verify(audioService).speak(req.capture(), eq(true));
    SynthesisRequest r = req.getValue();
    assertEquals("Grr!", r.text());
    assertSame(spec, r.voice());
    assertEquals(Emotion.ANGRY, r.emotion());
    assertSame(profile, r.profile());
    assertFalse("an NPC line is not a player line", r.player());
    assertFalse("dialogue lines are not translation-bypassed", r.skipTranslation());
  }

  @Test
  public void publicChatIsNeutralPlayerTranslationBypassed() {
    when(backend.isAvailable()).thenReturn(true);
    VoiceSpec spec = mock(VoiceSpec.class);
    when(voiceManager.resolve(Speaker.PLAYER, null)).thenReturn(new ResolvedSpeaker(spec, null));
    when(caveEchoPolicy.shouldEcho()).thenReturn(false);

    dispatcher.speakPublicChat("hello world");

    ArgumentCaptor<SynthesisRequest> req = ArgumentCaptor.forClass(SynthesisRequest.class);
    verify(audioService).speak(req.capture(), eq(false));
    SynthesisRequest r = req.getValue();
    assertEquals("hello world", r.text());
    assertEquals(Emotion.NEUTRAL, r.emotion());
    assertTrue("public chat is a player line", r.player());
    assertTrue("public chat bypasses translation/styles", r.skipTranslation());
  }

  @Test
  public void narrationIsNeutralNonPlayerAndStillTranslated() {
    when(backend.isAvailable()).thenReturn(true);
    VoiceSpec spec = mock(VoiceSpec.class);
    CharacterProfile profile = mock(CharacterProfile.class);
    when(voiceManager.resolveNarrator()).thenReturn(new ResolvedSpeaker(spec, profile));
    when(caveEchoPolicy.shouldEcho()).thenReturn(false);

    dispatcher.speakNarration("You find a key.");

    ArgumentCaptor<SynthesisRequest> req = ArgumentCaptor.forClass(SynthesisRequest.class);
    verify(audioService).speak(req.capture(), eq(false));
    SynthesisRequest r = req.getValue();
    assertEquals("You find a key.", r.text());
    assertSame(spec, r.voice());
    assertSame(profile, r.profile());
    assertEquals("a narration box carries no chat head", Emotion.NEUTRAL, r.emotion());
    assertFalse("narration is not the player speaking", r.player());
    assertFalse("narration is translated like dialogue", r.skipTranslation());
  }

  @Test
  public void narrationIsNotColouredByTheRoomThePlayerIsStandingIn() {
    when(backend.isAvailable()).thenReturn(true);
    when(voiceManager.resolveNarrator()).thenReturn(new ResolvedSpeaker(VoiceSpec.NARRATOR, null));
    when(caveEchoPolicy.shouldEcho()).thenReturn(true);

    dispatcher.speakNarration("You find a key.");

    verify(audioService).speak(any(SynthesisRequest.class), eq(false));
  }

  @Test
  public void narrationResolvesTheNarratorEveryTimeSoItsCacheKeyIsStable() {
    when(backend.isAvailable()).thenReturn(true);
    ResolvedSpeaker narrator = new ResolvedSpeaker(VoiceSpec.NARRATOR, null);
    when(voiceManager.resolveNarrator()).thenReturn(narrator);

    dispatcher.speakNarration("You find a key.");
    dispatcher.speakNarration("You find a key.");

    ArgumentCaptor<SynthesisRequest> req = ArgumentCaptor.forClass(SynthesisRequest.class);
    verify(audioService, times(2)).speak(req.capture(), anyBoolean());
    assertEquals(
        "both narrated lines resolve to the same voice key",
        req.getAllValues().get(0).voice().key(),
        req.getAllValues().get(1).voice().key());
  }

  @Test
  public void nothingIsSpokenWhenTheBackendIsUnavailable() {
    when(backend.isAvailable()).thenReturn(false);
    ResolvedSpeaker resolved = new ResolvedSpeaker(mock(VoiceSpec.class), null);
    when(voiceManager.resolve(Speaker.NPC, "Bob")).thenReturn(resolved);
    when(voiceManager.resolve(Speaker.PLAYER, null)).thenReturn(resolved);
    when(voiceManager.resolveNarrator()).thenReturn(resolved);

    dispatcher.speakDialogue("Grr!", Speaker.NPC, "Bob", 614);
    dispatcher.speakPublicChat("hello");
    dispatcher.speakNarration("You find a key.");

    verify(audioService, never()).speak(any(SynthesisRequest.class), anyBoolean());
  }
}
