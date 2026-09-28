package com.grahambartley.runelite.voiced.dialogue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.audio.AudioOutput;
import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.StreamingAudioPlayer;
import com.grahambartley.runelite.voiced.dialogue.panel.NpcVoicePanel;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceOverrideStore;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import com.grahambartley.runelite.voiced.dialogue.speech.BackendProvider;
import com.grahambartley.runelite.voiced.dialogue.speech.DialogueAudioService;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisBackend;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.NPCComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.junit.Test;

public class VoicedDialoguePluginTest {

  private static final String KEY_TRIGGER = "openRouterApiKey";

  @Test
  public void backendKeyChangeWarmsActiveBackendOnce() throws Exception {
    AtomicInteger warmCalls = new AtomicInteger();
    Harness harness = harness(warmCalls);

    harness.plugin.onConfigChanged(configChanged("voicedDialogue", KEY_TRIGGER));
    harness.audioService.awaitWarm();

    assertEquals(1, warmCalls.get());
  }

  @Test
  public void backendKeyChangeDropsTheRateLimitWindow() throws Exception {
    Harness harness = harness(new AtomicInteger());

    harness.plugin.onConfigChanged(configChanged("voicedDialogue", KEY_TRIGGER));

    assertEquals(2, harness.backend.rateLimitClears.get());
  }

  @Test
  public void unrelatedKeyOrGroupDoesNotWarm() throws Exception {
    AtomicInteger warmCalls = new AtomicInteger();
    Harness harness = harness(warmCalls);

    harness.plugin.onConfigChanged(configChanged("voicedDialogue", "volume"));
    harness.plugin.onConfigChanged(configChanged("otherPlugin", KEY_TRIGGER));

    assertEquals(0, warmCalls.get());
    assertEquals("nor the rate-limit clear", 0, harness.backend.rateLimitClears.get());
  }

  @Test
  public void configChangeWhileStoppedDoesNotThrow() {
    VoicedDialoguePlugin plugin = new VoicedDialoguePlugin();
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

  @Test
  public void aSpendTaskQueuedBeforeShutdownNeverRuns() throws Exception {
    Harness harness = harness(new AtomicInteger());
    DeferredExecutorService spendExecutor = new DeferredExecutorService();
    setField(harness.plugin, "spendExecutor", spendExecutor);
    AtomicInteger spendRuns = new AtomicInteger();

    submitSpendTask(harness.plugin, spendRuns::incrementAndGet);
    harness.plugin.shutDown();
    spendExecutor.runAll();

    assertEquals("a spend task queued at shutdown never runs", 0, spendRuns.get());
  }

  @Test
  public void aSpendTaskQueuedWhileRunningStillRuns() throws Exception {
    Harness harness = harness(new AtomicInteger());
    DeferredExecutorService spendExecutor = new DeferredExecutorService();
    setField(harness.plugin, "spendExecutor", spendExecutor);
    AtomicInteger spendRuns = new AtomicInteger();

    submitSpendTask(harness.plugin, spendRuns::incrementAndGet);
    spendExecutor.runAll();

    assertEquals("a spend task queued while running is run", 1, spendRuns.get());
  }

  @Test
  public void aWarmQueuedBeforeCloseNeverTouchesTheBackend() throws Exception {
    AtomicInteger warmCalls = new AtomicInteger();
    Harness harness = harness(warmCalls);
    AtomicInteger warmRuns = new AtomicInteger();

    harness.plugin.shutDown();
    harness.audioService.delegate.prewarm(warmRuns::incrementAndGet);

    assertEquals("a warm queued at close never reaches the backend", 0, warmRuns.get());
    assertEquals("and never warms it", 0, warmCalls.get());
  }

  @Test
  public void aWarmQueuedWhileALineIsPlayingStillWarms() throws Exception {
    AtomicInteger warmCalls = new AtomicInteger();
    Harness harness = harness(warmCalls);
    AtomicInteger warmRuns = new AtomicInteger();

    harness.audioService.delegate.speak(
        new SynthesisRequest(
            "A line",
            VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE),
            Emotion.NEUTRAL,
            null,
            true,
            false));
    harness.audioService.delegate.prewarm(warmRuns::incrementAndGet);
    harness.audioService.awaitWarm();

    assertEquals("a warm issued during a line still runs", 1, warmRuns.get());
  }

  private static void submitSpendTask(VoicedDialoguePlugin plugin, Runnable task) throws Exception {
    Method method = VoicedDialoguePlugin.class.getDeclaredMethod("submitSpendTask", Runnable.class);
    method.setAccessible(true);
    method.invoke(plugin, task);
  }

  private static final class SilentOutput implements AudioOutput {

    @Override
    public void stream(float[] samples, int sampleRate, int volumePercent) {}

    @Override
    public AudioStream beginStream(int volumePercent) {
      return new AudioStream() {
        @Override
        public void write(float[] samples, int sampleRate) {}

        @Override
        public void end() {}
      };
    }

    @Override
    public void setVolume(int volumePercent) {}

    @Override
    public void stop() {}

    @Override
    public void close() {}
  }

  private static final class DeferredExecutorService extends AbstractExecutorService {
    private final List<Runnable> tasks = new ArrayList<>();
    private volatile boolean shutdown;

    @Override
    public void execute(Runnable command) {
      tasks.add(command);
    }

    void runAll() {
      List<Runnable> snapshot = new ArrayList<>(tasks);
      tasks.clear();
      for (Runnable task : snapshot) {
        task.run();
      }
    }

    @Override
    public void shutdown() {
      shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      shutdown = true;
      return new ArrayList<>();
    }

    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    @Override
    public boolean isTerminated() {
      return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return shutdown;
    }
  }

  @Test
  public void anOverrideKeyChangeRefreshesThatOverride() throws Exception {
    ConfigManager configManager = mock(ConfigManager.class);
    VoicedDialoguePlugin plugin = new VoicedDialoguePlugin();
    setField(plugin, "voiceOverrideStore", new NpcVoiceOverrideStore(configManager));

    plugin.onConfigChanged(configChanged("voicedDialogue", "npcVoice_5"));

    verify(configManager).getConfiguration("voicedDialogue", "npcVoice_5");
  }

  @Test
  public void anotherPluginsKeyChangeLeavesOverridesAlone() throws Exception {
    ConfigManager configManager = mock(ConfigManager.class);
    VoicedDialoguePlugin plugin = new VoicedDialoguePlugin();
    setField(plugin, "voiceOverrideStore", new NpcVoiceOverrideStore(configManager));

    plugin.onConfigChanged(configChanged("otherPlugin", "npcVoice_5"));

    verify(configManager, never()).getConfiguration(anyString(), anyString());
  }

  @Test
  public void aProfileSwitchReloadsEveryOverride() throws Exception {
    ConfigManager configManager = mock(ConfigManager.class);
    when(configManager.getConfigurationKeys("voicedDialogue.npcVoice_"))
        .thenReturn(new ArrayList<>());
    VoicedDialoguePlugin plugin = new VoicedDialoguePlugin();
    setField(plugin, "voiceOverrideStore", new NpcVoiceOverrideStore(configManager));

    plugin.onProfileChanged(new ProfileChanged());

    verify(configManager).getConfigurationKeys("voicedDialogue.npcVoice_");
  }

  @Test
  public void aProfileSwitchBeforeStartUpIsIgnored() {
    new VoicedDialoguePlugin().onProfileChanged(new ProfileChanged());
  }

  @Test
  public void anOverrideKeyChangeRedrawsTheNpcVoicePanel() throws Exception {
    NpcVoicePanel panel = mock(NpcVoicePanel.class);
    VoicedDialoguePlugin plugin = new VoicedDialoguePlugin();
    setField(plugin, "npcVoicePanel", panel);

    plugin.onConfigChanged(configChanged("voicedDialogue", "npcVoice_11911"));
    plugin.onConfigChanged(configChanged("voicedDialogue", "volume"));

    verify(panel, times(1)).refreshLater();
  }

  @Test
  public void aProfileSwitchRedrawsTheNpcVoicePanel() throws Exception {
    NpcVoicePanel panel = mock(NpcVoicePanel.class);
    VoicedDialoguePlugin plugin = new VoicedDialoguePlugin();
    setField(plugin, "npcVoicePanel", panel);

    plugin.onProfileChanged(new ProfileChanged());

    verify(panel).refreshLater();
  }

  @Test
  public void shutDownRemovesTheSidePanelButton() throws Exception {
    ClientToolbar toolbar = mock(ClientToolbar.class);
    NavigationButton button = NavigationButton.builder().tooltip("t").build();
    VoicedDialoguePlugin plugin = new VoicedDialoguePlugin();
    setField(plugin, "clientToolbar", toolbar);
    setField(plugin, "navigationButton", button);

    plugin.shutDown();

    verify(toolbar).removeNavigation(button);
  }

  @Test
  public void npcNamesAreReadOnTheClientThreadAndBlankOnesSkipped() throws Exception {
    Client client = mock(Client.class);
    NPCComposition named = mock(NPCComposition.class);
    when(named.getName()).thenReturn("Mystery trader");
    NPCComposition unnamed = mock(NPCComposition.class);
    when(unnamed.getName()).thenReturn("null");
    when(client.getNpcDefinition(1)).thenReturn(named);
    when(client.getNpcDefinition(2)).thenReturn(unnamed);
    when(client.getNpcDefinition(3)).thenReturn(null);
    ClientThread clientThread = mock(ClientThread.class);
    List<Runnable> clientTasks = new ArrayList<>();
    doAnswer(
            invocation -> {
              clientTasks.add(invocation.getArgument(0));
              return null;
            })
        .when(clientThread)
        .invoke(any(Runnable.class));
    VoicedDialoguePlugin plugin = new VoicedDialoguePlugin();
    setField(plugin, "client", client);
    setField(plugin, "clientThread", clientThread);
    List<Map<Integer, String>> resolved = new ArrayList<>();

    plugin.resolveNpcNames(new HashSet<>(Arrays.asList(1, 2, 3)), resolved::add);
    assertTrue(resolved.isEmpty());
    clientTasks.forEach(Runnable::run);

    assertEquals(
        Collections.singletonList(Collections.singletonMap(1, "Mystery trader")), resolved);
  }

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

  private static Harness harness(AtomicInteger warmCalls) throws Exception {
    StubBackend cloud = new StubBackend("cloud-openrouter", warmCalls);
    BackendProvider provider = new BackendProvider(cloud);
    DialogueAudioService audioService =
        new DialogueAudioService(
            provider, new SilentOutput(), StreamingAudioPlayer::new, null, 1, 1, () -> 100);

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

  private static final class AwaitableAudioService {
    final DialogueAudioService delegate;

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
