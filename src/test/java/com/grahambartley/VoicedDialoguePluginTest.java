package com.grahambartley;

import static org.junit.Assert.assertEquals;

import com.grahambartley.VoicedDialogueConfig.VoiceBackend;
import com.grahambartley.synthesis.BackendProvider;
import com.grahambartley.synthesis.Emotion;
import com.grahambartley.synthesis.SynthesisBackend;
import com.grahambartley.synthesis.SynthesisRequest;
import com.grahambartley.tts.DialogueAudioService;
import com.grahambartley.tts.Pcm;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.events.ConfigChanged;
import org.junit.Test;

/**
 * Verifies the plugin's runtime backend-switch warm-up orchestration (#75): a {@link ConfigChanged}
 * for the plugin group and a backend-affecting key re-runs the active backend's off-thread warm-up
 * exactly once, while unrelated groups/keys and a stopped/shutting-down plugin do nothing. The pure
 * decision behind the trigger lives in {@link BackendWarmUpPolicy}.
 */
public class VoicedDialoguePluginTest {

  /**
   * A backend-key change in the plugin group drives the real off-thread pipeline end to end: {@code
   * prewarm} -> executor -> {@code warmUpActive} -> the selected (non-Kokoro) backend's {@code
   * warmUp}, exactly once.
   */
  @Test
  public void backendKeyChangeWarmsActiveBackendOnce() throws Exception {
    AtomicInteger warmCalls = new AtomicInteger();
    StubConfig config = new StubConfig();
    config.backend = VoiceBackend.CLOUD; // so warmUpActive targets the non-Kokoro backend
    Harness harness = harness(config, warmCalls);

    harness.plugin.onConfigChanged(configChanged("voicedDialogue", "voiceBackend"));
    harness.audioService.awaitWarm();

    assertEquals(1, warmCalls.get());
  }

  /** Unrelated keys and groups never reach the warm-up path. */
  @Test
  public void unrelatedKeyOrGroupDoesNotWarm() throws Exception {
    AtomicInteger warmCalls = new AtomicInteger();
    StubConfig config = new StubConfig();
    config.backend = VoiceBackend.CLOUD;
    Harness harness = harness(config, warmCalls);

    harness.plugin.onConfigChanged(configChanged("voicedDialogue", "volume"));
    harness.plugin.onConfigChanged(configChanged("otherPlugin", "voiceBackend"));

    assertEquals(0, warmCalls.get());
  }

  /**
   * A config change while the plugin is stopped/shutting down (null collaborators) no-ops safely.
   */
  @Test
  public void configChangeWhileStoppedDoesNotThrow() throws Exception {
    VoicedDialoguePlugin plugin = new VoicedDialoguePlugin();
    // audioService and backendProvider are null (never started / already shut down).
    plugin.onConfigChanged(configChanged("voicedDialogue", "voiceBackend"));
  }

  // --- helpers -------------------------------------------------------------

  private static ConfigChanged configChanged(String group, String key) {
    ConfigChanged event = new ConfigChanged();
    event.setGroup(group);
    event.setKey(key);
    return event;
  }

  /** Plugin wired with a real DialogueAudioService and BackendProvider over counting stubs. */
  private static Harness harness(StubConfig config, AtomicInteger warmCalls) throws Exception {
    SynthesisBackend kokoro = new StubBackend(BackendProvider.LOCAL_KOKORO_ID, warmCalls);
    SynthesisBackend cloud = new StubBackend("cloud-openrouter", warmCalls);
    BackendProvider provider = new BackendProvider(config, kokoro, cloud);
    DialogueAudioService audioService =
        new DialogueAudioService(provider, null, null, 1, 1, () -> 100);

    VoicedDialoguePlugin plugin = new VoicedDialoguePlugin();
    setField(plugin, "audioService", audioService);
    setField(plugin, "backendProvider", provider);
    return new Harness(plugin, audioService);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = VoicedDialoguePlugin.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  /** Holds the plugin plus the audio service so a test can await the off-thread warm. */
  private static final class Harness {
    final VoicedDialoguePlugin plugin;
    final AwaitableAudioService audioService;

    Harness(VoicedDialoguePlugin plugin, DialogueAudioService audioService) {
      this.plugin = plugin;
      this.audioService = new AwaitableAudioService(audioService);
    }
  }

  /**
   * Lets a test block until the single-threaded pipeline drains, so the off-thread {@code warmUp}
   * has run before the assertion. Submits a sentinel {@code prewarm} and waits for it to execute.
   */
  private static final class AwaitableAudioService {
    private final DialogueAudioService delegate;

    AwaitableAudioService(DialogueAudioService delegate) {
      this.delegate = delegate;
    }

    void awaitWarm() throws InterruptedException {
      java.util.concurrent.CountDownLatch drained = new java.util.concurrent.CountDownLatch(1);
      delegate.prewarm(drained::countDown);
      if (!drained.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
        throw new AssertionError("warm-up pipeline did not drain in time");
      }
    }
  }

  private static final class StubConfig implements VoicedDialogueConfig {
    private VoiceBackend backend = VoiceBackend.LOCAL;

    @Override
    public VoiceBackend voiceBackend() {
      return backend;
    }
  }

  /** Counts {@code warmUp} calls so the test can assert the active backend was warmed once. */
  private static final class StubBackend implements SynthesisBackend {
    private final String id;
    private final AtomicInteger warmCalls;

    StubBackend(String id, AtomicInteger warmCalls) {
      this.id = id;
      this.warmCalls = warmCalls;
    }

    @Override
    public String id() {
      return id;
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
      return new Pcm(new float[] {0f}, 24_000);
    }

    @Override
    public void warmUp() {
      warmCalls.incrementAndGet();
    }
  }
}
