package com.grahambartley.runelite.voiced.dialogue.speech.model;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.TestPcm;
import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import java.util.EnumSet;
import org.junit.Test;

public class GeminiTtsModelTest {

  private static final CharacterProfile GNOME =
      new CharacterProfile("Gnome", "Irish accent", "Cheerful", "Quick pace");

  private final GeminiTtsModel model = new GeminiTtsModel();

  @Test
  public void identifiesTheGeminiModelAndPcmFormat() {
    assertEquals("google/gemini-3.8-flash-tts", model.modelId());
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
  public void speechStyleCarriesProfileAndEmotion() {
    assertEquals(
        "Audio profile: Gnome, a character in a medieval fantasy world. Accent: Irish accent."
            + " Style: Cheerful. Pace: Quick pace. Sounding happy.",
        model.speechStyle(GNOME, Emotion.HAPPY));
  }

  @Test
  public void speechStyleWithSpeedAddsASpeedDirection() {
    assertEquals(
        "Audio profile: Gnome, a character in a medieval fantasy world. Accent: Irish accent."
            + " Style: Cheerful. Pace: Quick pace. Speaking at 80% of normal speed.",
        model.speechStyle(GNOME, Emotion.NEUTRAL, 80));
  }

  @Test
  public void speechMetadataWrapsTheStyle() {
    JsonObject metadata = model.speechMetadata("Calm.");
    assertEquals(1, metadata.size());
    assertEquals("Calm.", metadata.get("style").getAsString());
  }

  @Test
  public void decodesRawPcmAt24k() {
    Pcm pcm = model.decodeResponse(TestPcm.raw(new short[] {0, 16384, -16384}));
    assertEquals(24_000, pcm.getSampleRate());
    assertEquals(3, pcm.getSamples().length);
  }
}
