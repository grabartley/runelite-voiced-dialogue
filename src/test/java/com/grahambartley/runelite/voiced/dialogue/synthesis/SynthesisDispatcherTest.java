package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.tts.CaveEchoPolicy;
import com.grahambartley.runelite.voiced.dialogue.tts.DialogueAudioService;
import com.grahambartley.runelite.voiced.dialogue.voice.EmotionResolver;
import com.grahambartley.runelite.voiced.dialogue.voice.ResolvedSpeaker;
import com.grahambartley.runelite.voiced.dialogue.voice.Speaker;
import com.grahambartley.runelite.voiced.dialogue.voice.VoiceManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * The single place a dialogue or public-chat line becomes a {@link SynthesisRequest}: the resolved
 * speaker, emotion, player flag, and the cave-echo gate are all assembled here, behind one
 * availability guard, and handed to the off-thread audio service.
 */
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
  public void nothingIsSpokenWhenTheBackendIsUnavailable() {
    when(backend.isAvailable()).thenReturn(false);
    ResolvedSpeaker resolved = new ResolvedSpeaker(mock(VoiceSpec.class), null);
    when(voiceManager.resolve(Speaker.NPC, "Bob")).thenReturn(resolved);
    when(voiceManager.resolve(Speaker.PLAYER, null)).thenReturn(resolved);

    dispatcher.speakDialogue("Grr!", Speaker.NPC, "Bob", 614);
    dispatcher.speakPublicChat("hello");

    verify(audioService, never()).speak(any(SynthesisRequest.class), anyBoolean());
  }
}
