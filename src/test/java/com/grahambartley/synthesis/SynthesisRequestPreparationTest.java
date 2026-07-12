package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.grahambartley.voice.VoiceManager.NPCGender;
import com.grahambartley.voice.VoiceManager.NPCRace;
import org.junit.Test;

public class SynthesisRequestPreparationTest {

  private static final VoiceSpec VOICE = VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE);

  @Test
  public void preparedSettingsAndContextSurviveAnEmotionChange() {
    SynthesisRequest request =
        new SynthesisRequest("Yes.", VOICE, Emotion.NEUTRAL, null, false, true)
            .withContext("Will you help me?")
            .withBackendSettings(
                "French pirate speak",
                "fr-FR",
                "Use native French pronunciation.",
                true,
                3,
                120,
                90);

    SynthesisRequest changed = request.withEmotion(Emotion.HAPPY);

    assertEquals("Will you help me?", changed.context());
    assertEquals("French pirate speak", changed.preparedLanguage());
    assertEquals("fr-FR", changed.preparedLanguageCode());
    assertEquals(3, changed.preparedCreativity());
    assertEquals(120, changed.preparedSpeedPercent());
  }

  @Test
  public void contextIsBoundedToShortPlayerReplies() {
    assertNull(
        new SynthesisRequest(
                "This player reply is deliberately longer than eighty characters, so context is disabled.",
                VOICE,
                Emotion.NEUTRAL,
                null,
                false,
                true)
            .withContext("Previous line")
            .context());
    assertNull(
        new SynthesisRequest("Yes.", VOICE, Emotion.NEUTRAL)
            .withContext("Previous line")
            .context());
  }
}
