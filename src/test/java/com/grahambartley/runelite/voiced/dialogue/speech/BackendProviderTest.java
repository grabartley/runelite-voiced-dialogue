package com.grahambartley.runelite.voiced.dialogue.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
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
    int closeCalls;
    int rateLimitClears;

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
    public void clearRateLimit() {
      rateLimitClears++;
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

    @Override
    public void close() {
      closeCalls++;
    }
  }

  private static SynthesisRequest req(Emotion emotion) {
    return new SynthesisRequest("hi", VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), emotion);
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

    provider.synthesizeWith(provider.active(), req(Emotion.ANGRY));

    assertEquals("backend never sees an unsupported emotion", Emotion.NEUTRAL, backend.lastEmotion);
    assertEquals(1, backend.synthCalls);
  }

  @Test
  public void supportedEmotionIsPassedThroughUnchanged() {
    StubBackend cloud = new StubBackend("cloud-openrouter", true, EnumSet.allOf(Emotion.class));
    BackendProvider provider = new BackendProvider(cloud);

    provider.synthesizeWith(provider.active(), req(Emotion.ANGRY));

    assertEquals("a supported emotion is preserved", Emotion.ANGRY, cloud.lastEmotion);
  }

  @Test
  public void cloudFullEmotionSetIsNotDowngraded() {
    StubBackend cloud = new StubBackend("cloud-openrouter", true, EnumSet.allOf(Emotion.class));
    BackendProvider provider = new BackendProvider(cloud);

    provider.synthesizeWith(provider.active(), req(Emotion.SCARED));

    assertEquals("Cloud supports the full set, so no downgrade", Emotion.SCARED, cloud.lastEmotion);
  }

  @Test
  public void activeFollowsTheConfiguredProviderLive() {
    StubBackend openRouter =
        new StubBackend("cloud-openrouter", true, EnumSet.allOf(Emotion.class));
    StubBackend aiStudio =
        new StubBackend("cloud-google-ai-studio", true, EnumSet.allOf(Emotion.class));
    VoicedDialogueConfig.TtsProvider[] selected = {VoicedDialogueConfig.TtsProvider.OPENROUTER};
    BackendProvider provider = new BackendProvider(openRouter, aiStudio, () -> selected[0]);

    assertSame(openRouter, provider.active());
    selected[0] = VoicedDialogueConfig.TtsProvider.GOOGLE_AI_STUDIO;
    assertSame("provider switch takes effect with no restart", aiStudio, provider.active());
  }

  @Test
  public void warmUpActiveWarmsOnlyTheSelectedBackend() {
    StubBackend openRouter =
        new StubBackend("cloud-openrouter", true, EnumSet.allOf(Emotion.class));
    StubBackend aiStudio =
        new StubBackend("cloud-google-ai-studio", true, EnumSet.allOf(Emotion.class));
    BackendProvider provider =
        new BackendProvider(
            openRouter, aiStudio, () -> VoicedDialogueConfig.TtsProvider.GOOGLE_AI_STUDIO);

    provider.warmUpActive();

    assertEquals(0, openRouter.warmCalls);
    assertEquals(1, aiStudio.warmCalls);
  }

  @Test
  public void closeReleasesBothProviderBackends() {
    StubBackend openRouter =
        new StubBackend("cloud-openrouter", true, EnumSet.allOf(Emotion.class));
    StubBackend aiStudio =
        new StubBackend("cloud-google-ai-studio", true, EnumSet.allOf(Emotion.class));
    BackendProvider provider =
        new BackendProvider(
            openRouter, aiStudio, () -> VoicedDialogueConfig.TtsProvider.OPENROUTER);

    provider.close();

    assertEquals(1, openRouter.closeCalls);
    assertEquals(1, aiStudio.closeCalls);
  }

  @Test
  public void closeReleasesASingleFixedBackendOnce() {
    StubBackend cloud = new StubBackend("cloud-openrouter", true, EnumSet.allOf(Emotion.class));
    BackendProvider provider = new BackendProvider(cloud);

    provider.close();

    assertEquals(1, cloud.closeCalls);
  }

  @Test
  public void clearingRateLimitsReachesBothBackends() {
    StubBackend openRouter = new StubBackend("or", true, EnumSet.allOf(Emotion.class));
    StubBackend aiStudio = new StubBackend("ai", true, EnumSet.allOf(Emotion.class));
    BackendProvider provider =
        new BackendProvider(
            openRouter, aiStudio, () -> VoicedDialogueConfig.TtsProvider.GOOGLE_AI_STUDIO);

    provider.clearRateLimits();

    assertEquals(
        "a changed key invalidates the idle backend's window too", 1, aiStudio.rateLimitClears);
    assertEquals(1, openRouter.rateLimitClears);
  }
}
