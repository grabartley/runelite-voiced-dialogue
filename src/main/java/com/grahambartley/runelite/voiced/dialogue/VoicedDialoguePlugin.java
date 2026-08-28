package com.grahambartley.runelite.voiced.dialogue;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.grahambartley.runelite.voiced.dialogue.data.LearnedNpcStore;
import com.grahambartley.runelite.voiced.dialogue.data.NpcLearningService;
import com.grahambartley.runelite.voiced.dialogue.data.WikiNpcClient;
import com.grahambartley.runelite.voiced.dialogue.dialogue.ChatNoticeManager;
import com.grahambartley.runelite.voiced.dialogue.dialogue.DialoguePrefetchCoordinator;
import com.grahambartley.runelite.voiced.dialogue.dialogue.DialoguePrefetcher;
import com.grahambartley.runelite.voiced.dialogue.dialogue.DialogueTextCleaner;
import com.grahambartley.runelite.voiced.dialogue.dialogue.DialogueWatcher;
import com.grahambartley.runelite.voiced.dialogue.dialogue.DialogueWidgetReader;
import com.grahambartley.runelite.voiced.dialogue.dialogue.PublicChatPolicy;
import com.grahambartley.runelite.voiced.dialogue.synthesis.BackendProvider;
import com.grahambartley.runelite.voiced.dialogue.synthesis.BackendWarmUpPolicy;
import com.grahambartley.runelite.voiced.dialogue.synthesis.GeminiAiStudioTtsBackend;
import com.grahambartley.runelite.voiced.dialogue.synthesis.OpenRouterCreditMeter;
import com.grahambartley.runelite.voiced.dialogue.synthesis.OpenRouterTtsBackend;
import com.grahambartley.runelite.voiced.dialogue.synthesis.OpenRouterUsageClient;
import com.grahambartley.runelite.voiced.dialogue.synthesis.ProfanityFilter;
import com.grahambartley.runelite.voiced.dialogue.synthesis.ProviderDefaultPolicy;
import com.grahambartley.runelite.voiced.dialogue.synthesis.SpendReport;
import com.grahambartley.runelite.voiced.dialogue.synthesis.SpendTracker;
import com.grahambartley.runelite.voiced.dialogue.synthesis.SynthesisDispatcher;
import com.grahambartley.runelite.voiced.dialogue.tts.CaveEchoPolicy;
import com.grahambartley.runelite.voiced.dialogue.tts.DialogueAudioService;
import com.grahambartley.runelite.voiced.dialogue.tts.DiskAudioCache;
import com.grahambartley.runelite.voiced.dialogue.tts.StreamingAudioPlayer;
import com.grahambartley.runelite.voiced.dialogue.voice.EmotionResolver;
import com.grahambartley.runelite.voiced.dialogue.voice.ProfileResolver;
import com.grahambartley.runelite.voiced.dialogue.voice.VoiceManager;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
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

  /**
   * Session-only counters behind {@code ::voicedspend}. Built fresh on every start-up, so the
   * totals reset with the plugin and nothing is ever persisted.
   */
  private SpendTracker spendTracker;

  /** Reads what the OpenRouter key has actually spent, so the readout quotes a billed figure. */
  private OpenRouterUsageClient usageClient;

  private OpenRouterCreditMeter creditMeter;

  /**
   * Dedicated daemon thread for the spend readout's balance reads, so a command never touches the
   * network on the game thread and never queues behind synthesis.
   */
  private ExecutorService spendExecutor;

  @Override
  protected void startUp() {
    pinProviderForExistingOpenRouterPlayers();
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

    // Cloud-only: dialogue is voiced through the configured provider (OpenRouter or Google AI
    // Studio), resolved live so switching needs no restart. A backend reports available only once
    // its API key is set, and a line it cannot voice is left silent (with a one-time notice)
    // rather than routed to the other provider. No model or native binaries ship in the plugin
    // jar.
    spendTracker = new SpendTracker();
    usageClient = new OpenRouterUsageClient(okHttpClient, gson);
    creditMeter = new OpenRouterCreditMeter();
    spendExecutor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "tts-spend");
              t.setDaemon(true);
              return t;
            });
    // Take the session's starting balance now, off the game thread, so the first ::voicedspend has
    // something to subtract from. A key entered later re-baselines through onConfigChanged.
    captureOpenRouterBaseline();
    OpenRouterTtsBackend openRouterBackend = new OpenRouterTtsBackend(okHttpClient, config, gson);
    openRouterBackend.setNotice(noticeManager::notifyFromBackendThread);
    openRouterBackend.setSpendTracker(spendTracker);
    GeminiAiStudioTtsBackend aiStudioBackend =
        new GeminiAiStudioTtsBackend(okHttpClient, config, gson);
    aiStudioBackend.setNotice(noticeManager::notifyFromBackendThread);
    aiStudioBackend.setSpendTracker(spendTracker);
    backendProvider = new BackendProvider(openRouterBackend, aiStudioBackend, config::ttsProvider);
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
            config::volume,
            config::streamPlayback);
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
    spendTracker = null;
    usageClient = null;
    creditMeter = null;
    if (spendExecutor != null) {
      spendExecutor.shutdownNow();
      spendExecutor = null;
    }
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
    log.info("VoicedDialogue stopped");
  }

  @Subscribe
  public void onGameTick(final GameTick tick) {
    if (noticeManager == null || backendProvider == null) {
      return;
    }
    noticeManager.maybeShowOnboarding();
    noticeManager.maybeWarnMissingCloudKey(backendProvider.active());
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
   * Answers {@code ::voicedspend} with this session's billable cloud usage: lines voiced, lines
   * prefetched, characters actually sent, and an estimated cost, one chat line per provider used.
   * Cache hits cost nothing and are counted nowhere, so a session spent replaying known lines reads
   * as zero. Runs on the client thread, where the event is dispatched, and touches no network.
   */
  @Subscribe
  public void onCommandExecuted(CommandExecuted event) {
    if (!SpendReport.matches(event.getCommand()) || spendTracker == null || noticeManager == null) {
      return;
    }
    List<SpendTracker.ProviderSpend> snapshot = spendTracker.snapshot();
    String key = config.openRouterApiKey();
    submitSpendTask(
        () -> {
          // OpenRouter states what the key has spent; reading it is a network call, so it happens
          // here and the finished lines hop back to the client thread to be posted.
          Double spent = creditMeter.spentSince(key, usageClient.fetchUsage(key));
          List<String> lines = SpendReport.lines(snapshot, spent);
          clientThread.invokeLater(
              () -> {
                if (noticeManager == null) {
                  return;
                }
                for (String line : lines) {
                  noticeManager.postNotice(line);
                }
              });
        });
  }

  /**
   * Reads the OpenRouter key's current all-time usage off the game thread and banks it as this
   * session's starting point. A no-op without a key; the {@link OpenRouterCreditMeter} ignores a
   * repeat for a key it already has a baseline for, so spend already made is never erased.
   */
  private void captureOpenRouterBaseline() {
    String key = config.openRouterApiKey();
    if (creditMeter.hasBaselineFor(key)) {
      return;
    }
    submitSpendTask(() -> creditMeter.recordBaseline(key, usageClient.fetchUsage(key)));
  }

  /** Runs a spend task on the dedicated thread, dropping it if the plugin is shutting down. */
  private void submitSpendTask(Runnable task) {
    ExecutorService executor = spendExecutor;
    if (executor == null) {
      return;
    }
    try {
      executor.execute(task);
    } catch (RejectedExecutionException ignored) {
      // Shutting down; the readout is not worth resurrecting a torn-down session for.
    }
  }

  /**
   * Makes a player's reliance on OpenRouter explicit in config, so a shipped provider they hold no
   * key for is never voiced through. Runs once per profile: after this the choice is recorded, so
   * {@link ProviderDefaultPolicy} declines to touch it again.
   */
  void pinProviderForExistingOpenRouterPlayers() {
    String stored =
        configManager.getConfiguration(
            VoicedDialogueConfig.GROUP, VoicedDialogueConfig.PROVIDER_KEY);
    if (!ProviderDefaultPolicy.shouldPinToOpenRouter(stored, config.openRouterApiKey())) {
      return;
    }
    configManager.setConfiguration(
        VoicedDialogueConfig.GROUP,
        VoicedDialogueConfig.PROVIDER_KEY,
        VoicedDialogueConfig.TtsProvider.OPENROUTER);
    log.info("Pinned this profile to OpenRouter, the provider it holds a key for");
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
    // An OpenRouter key swapped mid-session starts a different running total, so the old baseline
    // cannot be subtracted from the new key's usage.
    if ("openRouterApiKey".equals(event.getKey()) && creditMeter != null) {
      creditMeter.reset();
      captureOpenRouterBaseline();
    }
  }

  @Provides
  VoicedDialogueConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(VoicedDialogueConfig.class);
  }
}
