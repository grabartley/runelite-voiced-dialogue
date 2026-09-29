package com.grahambartley.runelite.voiced.dialogue.speech;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

public class SynthesisBackendTest {

  private static SynthesisRequest req() {
    return new SynthesisRequest(
        "Hello & welcome",
        VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE),
        Emotion.NEUTRAL,
        TestFixtures.TROLL_PROFILE,
        false,
        false);
  }

  @Test
  public void theDefaultStreamingImplementationFallsBackToBufferedAsOneChunk() {
    Pcm whole = new Pcm(new float[] {0.1f, -0.2f, 0.3f}, 24_000);
    SynthesisBackend buffered =
        new SynthesisBackend() {
          @Override
          public String id() {
            return "buffered-only";
          }

          @Override
          public boolean isAvailable() {
            return true;
          }

          @Override
          public EnumSet<Emotion> supportedEmotions() {
            return EnumSet.of(Emotion.NEUTRAL);
          }

          @Override
          public Pcm synthesize(SynthesisRequest request) {
            return whole;
          }
        };

    List<float[]> fed = new ArrayList<>();
    int[] rate = {0};
    Pcm result =
        buffered.synthesizeStreaming(
            req(),
            (chunk, r) -> {
              fed.add(chunk);
              rate[0] = r;
            });

    assertEquals("the buffered result is returned unchanged", whole, result);
    assertEquals("the whole line is delivered as a single chunk", 1, fed.size());
    assertArrayEquals(whole.getSamples(), fed.get(0), 0f);
    assertEquals("at the line's own sample rate", 24_000, rate[0]);
  }

  @Test
  public void theCacheNamespaceDefaultsToTheBackendId() {
    SynthesisBackend backend =
        new SynthesisBackend() {
          @Override
          public String id() {
            return "some-backend";
          }

          @Override
          public boolean isAvailable() {
            return true;
          }

          @Override
          public EnumSet<Emotion> supportedEmotions() {
            return EnumSet.of(Emotion.NEUTRAL);
          }

          @Override
          public Pcm synthesize(SynthesisRequest request) {
            return null;
          }
        };
    assertEquals("some-backend", backend.cacheNamespace());
  }
}
