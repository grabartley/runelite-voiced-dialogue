package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.grahambartley.VoiceManager.NPCGender;
import com.grahambartley.VoiceManager.NPCRace;
import com.grahambartley.synthesis.engine.EngineInstaller;
import com.grahambartley.synthesis.engine.ExternalEngineClient;
import com.grahambartley.tts.Pcm;
import java.nio.file.Paths;
import java.util.EnumSet;
import org.junit.Test;

/**
 * Behaviour of {@link LocalKokoroBackend}: it advertises neutral-only, becomes unavailable when the
 * engine cannot be installed (so the provider falls back), and delegates synthesis to the transport
 * client once warmed up. The installer and client are mocked so no real download or process
 * happens.
 */
public class LocalKokoroBackendTest {

  private static SynthesisRequest request() {
    return new SynthesisRequest(
        "hi", VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE), Emotion.NEUTRAL);
  }

  @Test
  public void supportsNeutralOnly() {
    LocalKokoroBackend backend =
        new LocalKokoroBackend(
            mock(EngineInstaller.class), launcher -> mock(ExternalEngineClient.class));
    assertEquals(EnumSet.of(Emotion.NEUTRAL), backend.supportedEmotions());
    assertEquals("local-kokoro", backend.id());
  }

  @Test
  public void availableOptimisticallyBeforeWarmUp() {
    LocalKokoroBackend backend =
        new LocalKokoroBackend(
            mock(EngineInstaller.class), launcher -> mock(ExternalEngineClient.class));
    // Before warm-up the provider should not pre-emptively fall back.
    assertTrue(backend.isAvailable());
  }

  @Test
  public void unavailableWhenInstallReturnsNull() {
    EngineInstaller installer = mock(EngineInstaller.class);
    when(installer.install()).thenReturn(null);

    LocalKokoroBackend backend =
        new LocalKokoroBackend(installer, launcher -> mock(ExternalEngineClient.class));
    backend.warmUp();

    assertFalse("a failed/absent install must make the backend unavailable", backend.isAvailable());
    // synthesize must return null (no crash) so the pipeline simply produces no audio for this
    // line.
    assertNull(backend.synthesize(request()));
  }

  @Test
  public void warmUpInstallsThenDelegatesSynthesis() throws Exception {
    EngineInstaller installer = mock(EngineInstaller.class);
    when(installer.install())
        .thenReturn(
            new EngineInstaller.Installed(Paths.get("/fake/kokoro-engine"), "kokoro", "1.0.0"));

    ExternalEngineClient client = mock(ExternalEngineClient.class);
    when(client.isHealthy()).thenReturn(true);
    Pcm expected = new Pcm(new float[] {0.1f, 0.2f}, 24000);
    when(client.synthesize(org.mockito.ArgumentMatchers.any())).thenReturn(expected);

    LocalKokoroBackend backend = new LocalKokoroBackend(installer, launcher -> client);
    backend.warmUp();

    assertTrue("available once the client reports healthy", backend.isAvailable());
    Pcm result = backend.synthesize(request());
    assertEquals(expected, result);
    assertEquals(24000, result.getSampleRate());
  }
}
