package com.grahambartley;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.grahambartley.data.LearnedNpcStore;
import com.grahambartley.data.NpcLearningService;
import com.grahambartley.data.WikiNpcClient;
import com.grahambartley.dialogue.ChatNoticeManager;
import com.grahambartley.dialogue.DialoguePrefetchCoordinator;
import com.grahambartley.dialogue.DialoguePrefetcher;
import com.grahambartley.dialogue.DialogueTextCleaner;
import com.grahambartley.dialogue.DialogueWatcher;
import com.grahambartley.dialogue.DialogueWidgetReader;
import com.grahambartley.dialogue.PublicChatPolicy;
import com.grahambartley.synthesis.BackendProvider;
import com.grahambartley.synthesis.BackendWarmUpPolicy;
import com.grahambartley.synthesis.OpenRouterTtsBackend;
import com.grahambartley.synthesis.ProfanityFilter;
import com.grahambartley.synthesis.SynthesisDispatcher;
import com.grahambartley.tts.CaveEchoPolicy;
import com.grahambartley.tts.DialogueAudioService;
import com.grahambartley.tts.DiskAudioCache;
import com.grahambartley.tts.StreamingAudioPlayer;
import com.grahambartley.voice.EmotionResolver;
import com.grahambartley.voice.ProfileResolver;
import com.grahambartley.voice.VoiceManager;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.OkHttpClient;

/**
 * RuneLite entry point for Voiced Dialogue. Owns only the plugin lifecycle, the RuneLite event
 * hooks, and dependency injection; all dialogue, synthesis, emotion, cave-echo, prefetch, and
 * notice behaviour lives in focused collaborators wired up in {@link #startUp}.
 */
@Slf4j
@PluginDescriptor(name = "Voiced Dialogue")
public class VoicedDialoguePlugin extends Plugin {

  /** Cache enough recent lines that loops of NPC chatter replay instantly without re-synthesis. */
  private static final int CACHE_SIZE = 64;

  /** Tiny backlog so a burst of dialogue ticks never blocks the game thread on enqueue. */
  private static final int QUEUE_CAPACITY = 4;

  @Inject private Client client;

  @Inject private VoicedDialogueConfig config;

  /**
   * Injected per Hub rules: never {@code new OkHttpClient()} / {@code new Gson()} in plugin code.
   */
  @Inject private OkHttpClient okHttpClient;

  @Inject private Gson gson;

  @Inject private ConfigManager configManager;

  @Inject private ClientThread clientThread;

  private BackendProvider backendProvider;

  private DialogueAudioService audioService;

  /** Dedicated daemon thread for off-game-thread wiki NPC lookups (the auto-learn fallback). */
  private ExecutorService wikiExecutor;

  private ChatNoticeManager noticeManager;

  private DialogueTextCleaner textCleaner;

  private SynthesisDispatcher synthesisDispatcher;

  private DialogueWatcher dialogueWatcher;

  @Override
  protected void startUp() {
    VoiceManager voiceManager = new VoiceManager(config, client);

    Path ttsDir = RuneLite.RUNELITE_DIR.toPath().resolve("voiced-dialogue");
    // Runtime "learn a new NPC" fallback: the learned cache is always consulted (so previously
    // learned NPCs voice correctly even with the toggle off), while new wiki lookups are gated by
    // the config toggle. Lookups run on a dedicated daemon thread, never the game thread.
    LearnedNpcStore learnedStore = new LearnedNpcStore(ttsDir.resolve("learned-npcs.json"), gson);
    wikiExecutor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "tts-wiki-learn");
              t.setDaemon(true);
              return t;
            });
    NpcLearningService learningService =
        new NpcLearningService(
            new WikiNpcClient(okHttpClient, gson),
            learnedStore,
            wikiExecutor,
            config::autoLearnNewNpcs);
    voiceManager.enableLearning(learnedStore, learningService);

    noticeManager = new ChatNoticeManager(client, configManager, clientThread, config);

    // Cloud-only: dialogue is voiced through OpenRouter. The backend reports available only once an
    // API key is set, and a line it cannot voice is left silent (with a one-time notice) rather
    // than routed anywhere else. No model or native binaries ship in the plugin jar.
    OpenRouterTtsBackend cloudBackend = new OpenRouterTtsBackend(okHttpClient, config, gson);
    cloudBackend.setNotice(noticeManager::notifyFromBackendThread);
    backendProvider = new BackendProvider(cloudBackend);
    // Persistent on-disk cache under the plugin's RuneLite dir; on by default so repeated lines
    // survive restarts and the cloud backend is not re-billed. Opt-out via config.
    DiskAudioCache diskCache =
        config.persistentCache()
            ? new DiskAudioCache(ttsDir.resolve("cache"), config.cacheSizeLimitMiB() * 1024L * 1024)
            : null;
    audioService =
        new DialogueAudioService(
            backendProvider,
            new StreamingAudioPlayer(),
            diskCache,
            CACHE_SIZE,
            QUEUE_CAPACITY,
            config::volume);
    // Warm the backend off the game thread so the first line is not the one that pays the cloud
    // connection handshake, and the game thread never blocks on it.
    audioService.prewarm(backendProvider::warmUpActive);
    // Speculative prefetch warms the cache for the dialogue options the player can see; it shares
    // the audio service's dedup and both cache tiers, runs off the game thread, and is gated by the
    // prefetch config (read live, so toggling it takes effect immediately).
    DialoguePrefetcher prefetcher =
        new DialoguePrefetcher(
            audioService::prefetch, audioService::cancelPrefetch, config::prefetch);

    textCleaner = new DialogueTextCleaner(new ProfanityFilter());
    ProfileResolver profileResolver = new ProfileResolver(voiceManager, config);
    CaveEchoPolicy caveEchoPolicy = new CaveEchoPolicy(client, config);
    synthesisDispatcher =
        new SynthesisDispatcher(
            voiceManager,
            new EmotionResolver(),
            profileResolver,
            caveEchoPolicy,
            config,
            backendProvider,
            audioService);
    DialoguePrefetchCoordinator prefetchCoordinator =
        new DialoguePrefetchCoordinator(
            voiceManager, profileResolver, textCleaner, prefetcher, backendProvider, config);
    dialogueWatcher =
        new DialogueWatcher(
            client,
            textCleaner,
            new DialogueWidgetReader(client),
            synthesisDispatcher,
            prefetchCoordinator,
            prefetcher,
            audioService);

    log.info("VoicedDialogue started");
  }

  @Override
  protected void shutDown() {
    noticeManager = null;
    synthesisDispatcher = null;
    dialogueWatcher = null;
    textCleaner = null;
    if (audioService != null) {
      audioService.close();
      audioService = null;
    }
    if (backendProvider != null) {
      backendProvider.close();
      backendProvider = null;
    }
    if (wikiExecutor != null) {
      wikiExecutor.shutdownNow();
      wikiExecutor = null;
    }
    log.info("TTS Plugin stopped");
  }

  @Subscribe
  public void onGameTick(final GameTick tick) {
    if (noticeManager == null || backendProvider == null) {
      return;
    }
    noticeManager.maybeShowOnboarding();
    noticeManager.maybeWarnMissingCloudKey(backendProvider.active().isAvailable());
    dialogueWatcher.tick();
  }

  /**
   * Voices the local player's own public chat (default off). Only the player's {@code PUBLICCHAT}
   * stream is spoken; other players' public messages, and every other chat type, are ignored. The
   * message is cleaned with the same {@link DialogueTextCleaner} as dialogue and voiced through the
   * player path with translation bypassed.
   */
  @Subscribe
  public void onChatMessage(ChatMessage event) {
    if (!config.voicePublicChat() || event.getType() != ChatMessageType.PUBLICCHAT) {
      return;
    }
    Player local = client.getLocalPlayer();
    if (local == null || !PublicChatPolicy.isSelfPublicChat(event.getName(), local.getName())) {
      return;
    }
    if (synthesisDispatcher == null) {
      return;
    }
    String cleaned = textCleaner.clean(event.getMessage());
    if (cleaned.isEmpty()) {
      return;
    }
    synthesisDispatcher.speakPublicChat(cleaned);
  }

  /**
   * Warms up the backend off the game thread when a backend-affecting config key changes at
   * runtime, so entering an OpenRouter key does the cloud connection handshake immediately rather
   * than starting cold on the next line. The decision lives in {@link BackendWarmUpPolicy}; the
   * work runs on the pipeline thread via {@link DialogueAudioService#prewarm}. No-ops safely when
   * the plugin is disabled or mid-shutdown.
   */
  @Subscribe
  public void onConfigChanged(ConfigChanged event) {
    if (!BackendWarmUpPolicy.affectsBackendWarmUp(event.getGroup(), event.getKey())) {
      return;
    }
    if (audioService == null || backendProvider == null) {
      return;
    }
    audioService.prewarm(backendProvider::warmUpActive);
  }

  @Provides
  VoicedDialogueConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(VoicedDialogueConfig.class);
  }
}
