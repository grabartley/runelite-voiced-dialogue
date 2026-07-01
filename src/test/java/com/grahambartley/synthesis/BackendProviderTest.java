package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.grahambartley.tts.Pcm;
import com.grahambartley.voice.VoiceManager.NPCGender;
import com.grahambartley.voice.VoiceManager.NPCRace;
import java.util.EnumSet;
import org.junit.Test;

public class BackendProviderTest {

  /** A backend with a configurable id, availability, and supported-emotion set. */
  private static final class StubBackend implements SynthesisBackend {
    private final String id;
    private final boolean available;
    private final EnumSet<Emotion> emotions;
    Emotion lastEmotion;
    int synthCalls;
    int warmCalls;

    StubBackend(String id, boolean available, EnumSet<Emotion> emotions) {
      this.id = id;
      this.available = available;
      this.emotions = emotions;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public boolean isAvailable() {
      return available;
    }

    @Override
    public EnumSet<Emotion> supportedEmotions() {
      return emotions;
    }

    @Override
    public Pcm synthesize(SynthesisRequest request) {
      synthCalls++;
      lastEmotion = request.emotion();
      return new Pcm(new float[] {0f}, 24_000);
    }

    @Override
    public void warmUp() {
      warmCalls++;
    }
  }

  private static SynthesisRequest req(Emotion emotion) {
    return new SynthesisRequest("hi", VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE), emotion);
  }

  @Test
  public void activeReturnsTheProvidedBackend() {
    StubBackend cloud = new StubBackend("cloud-openrouter", true, EnumSet.allOf(Emotion.class));
    BackendProvider provider = new BackendProvider(cloud);
    assertSame(cloud, provider.active());
  }

  @Test
  public void warmUpActiveWarmsTheBackend() {
    StubBackend cloud = new StubBackend("cloud-openrouter", true, EnumSet.allOf(Emotion.class));
    BackendProvider provider = new BackendProvider(cloud);

    provider.warmUpActive();

    assertEquals(1, cloud.warmCalls);
  }

  @Test
  public void unsupportedEmotionDowngradesToNeutralBeforeSynthesis() {
    StubBackend backend = new StubBackend("cloud-openrouter", true, EnumSet.of(Emotion.NEUTRAL));
    BackendProvider provider = new BackendProvider(backend);

    provider.synthesize(req(Emotion.ANGRY));

    assertEquals("backend never sees an unsupported emotion", Emotion.NEUTRAL, backend.lastEmotion);
    assertEquals(1, backend.synthCalls);
  }

  @Test
  public void supportedEmotionIsPassedThroughUnchanged() {
    StubBackend cloud = new StubBackend("cloud-openrouter", true, EnumSet.allOf(Emotion.class));
    BackendProvider provider = new BackendProvider(cloud);

    provider.synthesize(req(Emotion.ANGRY));

    assertEquals("a supported emotion is preserved", Emotion.ANGRY, cloud.lastEmotion);
  }

  @Test
  public void cloudFullEmotionSetIsNotDowngraded() {
    StubBackend cloud = new StubBackend("cloud-openrouter", true, EnumSet.allOf(Emotion.class));
    BackendProvider provider = new BackendProvider(cloud);

    provider.synthesize(req(Emotion.SCARED));

    assertEquals("Cloud supports the full set, so no downgrade", Emotion.SCARED, cloud.lastEmotion);
  }
}
