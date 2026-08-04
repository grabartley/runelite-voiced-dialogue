package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

import com.grahambartley.VoicedDialogueConfig;
import com.grahambartley.voice.VoiceManager.NPCGender;
import com.grahambartley.voice.VoiceManager.NPCRace;
import org.junit.Test;

/** Random resolves to one concrete style per line, so the cache key and the request agree. */
public class SpeakingStyleResolverTest {

  private static SynthesisRequest request(String text) {
    return new SynthesisRequest(
        text, VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE), Emotion.NEUTRAL);
  }

  @Test
  public void randomIsStablePerLineAndNeverResolvesToASentinel() {
    SynthesisRequest line = request("Hello there");

    VoicedDialogueConfig.SpeakingStyle first =
        SpeakingStyleResolver.resolve(VoicedDialogueConfig.SpeakingStyle.RANDOM, line);

    assertEquals(
        "the same line always resolves to the same style",
        first,
        SpeakingStyleResolver.resolve(VoicedDialogueConfig.SpeakingStyle.RANDOM, line));
    assertNotEquals(VoicedDialogueConfig.SpeakingStyle.NONE, first);
    assertNotEquals(VoicedDialogueConfig.SpeakingStyle.RANDOM, first);
  }

  @Test
  public void aConcreteStyleIsReturnedUnchanged() {
    assertSame(
        VoicedDialogueConfig.SpeakingStyle.PIRATE,
        SpeakingStyleResolver.resolve(
            VoicedDialogueConfig.SpeakingStyle.PIRATE, request("Hello there")));
  }

  @Test
  public void aMissingStyleFallsBackToNone() {
    assertSame(
        VoicedDialogueConfig.SpeakingStyle.NONE,
        SpeakingStyleResolver.resolve(null, request("Hello there")));
  }
}
