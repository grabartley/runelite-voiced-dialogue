package com.grahambartley.runelite.voiced.dialogue;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.speech.BackendProvider;
import com.grahambartley.runelite.voiced.dialogue.speech.DialogueAudioService;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisBackend;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisRequest;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import org.junit.Test;

/**
 * Verifies the plugin's runtime warm-up orchestration: a {@link ConfigChanged} for the plugin group
 * and a backend-affecting key (entering an OpenRouter key) re-runs the backend's off-thread warm-up
 * exactly once, while unrelated groups/keys and a stopped/shutting-down plugin do nothing. The pure
 * decision behind the trigger lives in {@code BackendWarmUpPolicy}.
 *
 * <p>Also covers the startup pin that records the provider a profile actually holds a key for, so a
 * profile is never left voicing through a provider it cannot reach; the decision itself lives in
 * {@code ProviderDefaultPolicy}.
 */
public class VoicedDialoguePluginTest {

  private static final String KEY_TRIGGER = "openRouterApiKey";

  /**
   * A key change in the plugin group drives the real off-thread pipeline end to end: {@code
   * prewarm} -> executor -> {@code warmUpActive} -> the backend's {@code warmUp}, exactly once.
   */
  @Test
  public void backendKeyChangeWarmsActiveBackendOnce() throws Exception {
    AtomicInteger warmCalls = new AtomicInteger();
    Harness harness = harness(warmCalls);

    harness.plugin.onConfigChanged(configChanged("voicedDialogue", KEY_TRIGGER));
    harness.audioService.awaitWarm();

    assertEquals(1, warmCalls.get());
  }

  /**
   * A rate-limit window is evidence about the credentials that earned it, so the same key change
   * drops it: the notice a stated window surfaces asks for exactly this change.
   */
  @Test
  public void backendKeyChangeDropsTheRateLimitWindow() throws Exception {
    Harness harness = harness(new AtomicInteger());

    harness.plugin.onConfigChanged(configChanged("voicedDialogue", KEY_TRIGGER));

    // The harness folds both provider slots onto one stub, so the fan-out reaches it twice.
    assertEquals(2, harness.backend.rateLimitClears.get());
  }

  /** Unrelated keys and groups never reach the warm-up path. */
  @Test
  public void unrelatedKeyOrGroupDoesNotWarm() throws Exception {
    AtomicInteger warmCalls = new AtomicInteger();
    Harness harness = harness(warmCalls);

    harness.plugin.onConfigChanged(configChanged("voicedDialogue", "volume"));
    harness.plugin.onConfigChanged(configChanged("otherPlugin", KEY_TRIGGER));

    assertEquals(0, warmCalls.get());
    assertEquals("nor the rate-limit clear", 0, harness.backend.rateLimitClears.get());
  }

  /**
   * A config change while the plugin is stopped/shutting down (null collaborators) no-ops safely.
   */
  @Test
  public void configChangeWhileStoppedDoesNotThrow() {
    VoicedDialoguePlugin plugin = new VoicedDialoguePlugin();
    // audioService and backendProvider are null (never started / already shut down).
    plugin.onConfigChanged(configChanged("voicedDialogue", KEY_TRIGGER));
  }

  @Test
  public void playerWithAnOpenRouterKeyIsPinnedToOpenRouter() throws Exception {
    ConfigManager configManager = mock(ConfigManager.class);
    when(configManager.getConfiguration(
            VoicedDialogueConfig.GROUP, VoicedDialogueConfig.PROVIDER_KEY))
        .thenReturn(null);
    VoicedDialoguePlugin plugin = pluginWith(configManager, "sk-or-abc");

    plugin.pinProviderWhenOnlyOpenRouterKeyed();

    verify(configManager)
        .setConfiguration(
            VoicedDialogueConfig.GROUP,
            VoicedDialogueConfig.PROVIDER_KEY,
            VoicedDialogueConfig.TtsProvider.OPENROUTER);
  }

  @Test
  public void playerWithNoOpenRouterKeyIsLeftOnTheShippedProvider() throws Exception {
    ConfigManager configManager = mock(ConfigManager.class);
    when(configManager.getConfiguration(
            VoicedDialogueConfig.GROUP, VoicedDialogueConfig.PROVIDER_KEY))
        .thenReturn(null);
    VoicedDialoguePlugin plugin = pluginWith(configManager, "");

    plugin.pinProviderWhenOnlyOpenRouterKeyed();

    verify(configManager, never()).setConfiguration(anyString(), anyString(), any());
  }

  @Test
  public void anExplicitProviderChoiceIsNeverRewritten() throws Exception {
    ConfigManager configManager = mock(ConfigManager.class);
    when(configManager.getConfiguration(
            VoicedDialogueConfig.GROUP, VoicedDialogueConfig.PROVIDER_KEY))
        .thenReturn(VoicedDialogueConfig.TtsProvider.GOOGLE_AI_STUDIO.name());
    VoicedDialoguePlugin plugin = pluginWith(configManager, "sk-or-abc");

    plugin.pinProviderWhenOnlyOpenRouterKeyed();

    verify(configManager, never()).setConfiguration(anyString(), anyString(), any());
  }

  // --- helpers -------------------------------------------------------------

  /** Plugin wired with just the config manager and OpenRouter key the pin decision reads. */
  private static VoicedDialoguePlugin pluginWith(ConfigManager configManager, String openRouterKey)
      throws Exception {
    VoicedDialoguePlugin plugin = new VoicedDialoguePlugin();
    setField(plugin, "configManager", configManager);
    setField(
        plugin,
        "config",
        new VoicedDialogueConfig() {
          @Override
          public String openRouterApiKey() {
            return openRouterKey;
          }
        });
    return plugin;
  }

  private static ConfigChanged configChanged(String group, String key) {
    ConfigChanged event = new ConfigChanged();
    event.setGroup(group);
    event.setKey(key);
    return event;
  }

  /** Plugin wired with a real DialogueAudioService and BackendProvider over a counting stub. */
  private static Harness harness(AtomicInteger warmCalls) throws Exception {
    StubBackend cloud = new StubBackend("cloud-openrouter", warmCalls);
    BackendProvider provider = new BackendProvider(cloud);
    DialogueAudioService audioService =
        new DialogueAudioService(provider, null, null, 1, 1, () -> 100, () -> true);

    VoicedDialoguePlugin plugin = new VoicedDialoguePlugin();
    setField(plugin, "audioService", audioService);
    setField(plugin, "backendProvider", provider);
    return new Harness(plugin, audioService, cloud);
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
    final StubBackend backend;

    Harness(VoicedDialoguePlugin plugin, DialogueAudioService audioService, StubBackend backend) {
      this.plugin = plugin;
      this.audioService = new AwaitableAudioService(audioService);
      this.backend = backend;
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

  /** Counts {@code warmUp} and rate-limit clears so a test can assert what a key change drove. */
  private static final class StubBackend implements SynthesisBackend {
    private final String id;
    private final AtomicInteger warmCalls;
    final AtomicInteger rateLimitClears = new AtomicInteger();

    StubBackend(String id, AtomicInteger warmCalls) {
      this.id = id;
      this.warmCalls = warmCalls;
    }

    @Override
    public void clearRateLimit() {
      rateLimitClears.incrementAndGet();
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
