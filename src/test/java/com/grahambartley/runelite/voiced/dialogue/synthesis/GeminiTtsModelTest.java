package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertEquals;

import com.grahambartley.runelite.voiced.dialogue.tts.Pcm;
import com.grahambartley.runelite.voiced.dialogue.voice.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.voice.NpcRace;
import java.util.EnumSet;
import org.junit.Test;

/** The Gemini model strategy: id, format, emotion set, and delegation to voice/style/decode. */
public class GeminiTtsModelTest {

  private final GeminiTtsModel model = new GeminiTtsModel();

  @Test
  public void identifiesTheGeminiModelAndPcmFormat() {
    assertEquals("google/gemini-3.1-flash-tts-preview", model.modelId());
    assertEquals("pcm", model.responseFormat());
  }

  @Test
  public void advertisesTheFullGeminiEmotionSet() {
    assertEquals(
        EnumSet.of(Emotion.NEUTRAL, Emotion.HAPPY, Emotion.SAD, Emotion.ANGRY, Emotion.SCARED),
        model.supportedEmotions());
  }

  @Test
  public void voiceComesFromTheGeminiVoiceMap() {
    VoiceSpec spec = VoiceSpec.npc(NpcRace.ELF, NpcGender.FEMALE);
    assertEquals(new GeminiVoiceMap().voiceFor(spec), model.voiceFor(spec));
  }

  @Test
  public void emotionIsRenderedAsAnInlineStyleTag() {
    assertEquals("[happy] Hello", model.styleInput("Hello", Emotion.HAPPY));
    assertEquals("Hello", model.styleInput("Hello", Emotion.NEUTRAL));
  }

  @Test
  public void decodesRawPcmAt24k() {
    Pcm pcm = model.decodeResponse(TestPcm.raw(new short[] {0, 16384, -16384}));
    assertEquals(24_000, pcm.getSampleRate());
    assertEquals(3, pcm.getSamples().length);
  }
}
