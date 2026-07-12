package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.grahambartley.VoicedDialogueConfig;
import com.grahambartley.voice.VoiceManager.NPCGender;
import com.grahambartley.voice.VoiceManager.NPCRace;
import org.junit.Test;

public class SpeakingStyleResolverTest {

  @Test
  public void randomIsStableAndNeverReturnsASentinel() {
    SynthesisRequest request =
        new SynthesisRequest(
            "Hello there", VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE), Emotion.NEUTRAL);

    VoicedDialogueConfig.SpeakingStyle first =
        SpeakingStyleResolver.resolve(VoicedDialogueConfig.SpeakingStyle.RANDOM, request);

    assertEquals(
        first, SpeakingStyleResolver.resolve(VoicedDialogueConfig.SpeakingStyle.RANDOM, request));
    assertNotEquals(VoicedDialogueConfig.SpeakingStyle.NONE, first);
    assertNotEquals(VoicedDialogueConfig.SpeakingStyle.RANDOM, first);
  }
}
