package com.grahambartley.runelite.voiced.dialogue;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.grahambartley.runelite.voiced.dialogue.audio.CaveEchoPolicy;
import com.grahambartley.runelite.voiced.dialogue.audio.StreamingAudioPlayer;
import com.grahambartley.runelite.voiced.dialogue.cache.DiskAudioCache;
import com.grahambartley.runelite.voiced.dialogue.capture.ChatNoticeManager;
import com.grahambartley.runelite.voiced.dialogue.capture.DialoguePrefetchCoordinator;
import com.grahambartley.runelite.voiced.dialogue.capture.DialoguePrefetcher;
import com.grahambartley.runelite.voiced.dialogue.capture.DialogueTextCleaner;
import com.grahambartley.runelite.voiced.dialogue.capture.DialogueWatcher;
import com.grahambartley.runelite.voiced.dialogue.capture.DialogueWidgetReader;
import com.grahambartley.runelite.voiced.dialogue.capture.PublicChatSpeaker;
import com.grahambartley.runelite.voiced.dialogue.profile.EmotionResolver;
import com.grahambartley.runelite.voiced.dialogue.profile.ProfanityFilter;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceManager;
import com.grahambartley.runelite.voiced.dialogue.speaker.LearnedNpcStore;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcLearningService;
import com.grahambartley.runelite.voiced.dialogue.speaker.WikiNpcClient;
import com.grahambartley.runelite.voiced.dialogue.speech.BackendProvider;
import com.grahambartley.runelite.voiced.dialogue.speech.BackendWarmUpPolicy;
import com.grahambartley.runelite.voiced.dialogue.speech.DialogueAudioService;
import com.grahambartley.runelite.voiced.dialogue.speech.GeminiAiStudioTtsBackend;
import com.grahambartley.runelite.voiced.dialogue.speech.OpenRouterCreditMeter;
import com.grahambartley.runelite.voiced.dialogue.speech.OpenRouterTtsBackend;
import com.grahambartley.runelite.voiced.dialogue.speech.OpenRouterUsageClient;
import com.grahambartley.runelite.voiced.dialogue.speech.ProviderDefaultPolicy;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import com.grahambartley.runelite.voiced.dialogue.speech.spend.SpendReport;
import com.grahambartley.runelite.voiced.dialogue.speech.spend.SpendTracker;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
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

  @Inject private ChatMessageManager chatMessageManager;

  private BackendProvider backendProvider;

  private DialogueAudioService audioService;

  private ExecutorService wikiExecutor;

  private ChatNoticeManager noticeManager;

  private DialogueWatcher dialogueWatcher;

  private PublicChatSpeaker publicChatSpeaker;

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
    pinProviderWhenOnlyOpenRouterKeyed();
    VoiceManager voiceManager = VoiceManager.create(config, client);

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
            new WikiNpcClient(okHttpClient), learnedStore, wikiExecutor, config::autoLearnNewNpcs);
    voiceManager.enableLearning(learnedStore, learningService);

    noticeManager = new ChatNoticeManager(client, configManager, clientThread, chatMessageManager);

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

    // A line the active backend cannot voice is left silent (with a one-time notice) rather than
    // routed to the other provider.
    OpenRouterTtsBackend openRouterBackend = new OpenRouterTtsBackend(okHttpClient, config, gson);
    openRouterBackend.setNotice(noticeManager::notifyFromBackendThread);
    openRouterBackend.setSpendTracker(spendTracker);
    GeminiAiStudioTtsBackend aiStudioBackend =
        new GeminiAiStudioTtsBackend(okHttpClient, config, gson);
    aiStudioBackend.setNotice(noticeManager::notifyFromBackendThread);
    aiStudioBackend.setSpendTracker(spendTracker);
    backendProvider = new BackendProvider(openRouterBackend, aiStudioBackend, config::ttsProvider);
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
    // the audio service's dedup and both cache tiers, and runs off the game thread.
    DialoguePrefetcher prefetcher =
        new DialoguePrefetcher(audioService::prefetch, audioService::cancelPrefetch);

    DialogueTextCleaner textCleaner = new DialogueTextCleaner(new ProfanityFilter());
    CaveEchoPolicy caveEchoPolicy = new CaveEchoPolicy(client, config);
    SynthesisDispatcher synthesisDispatcher =
        new SynthesisDispatcher(
            voiceManager,
            new EmotionResolver(),
            caveEchoPolicy,
            config,
            backendProvider,
            audioService);
    DialoguePrefetchCoordinator prefetchCoordinator =
        new DialoguePrefetchCoordinator(
            voiceManager, textCleaner, prefetcher, backendProvider, config);
    dialogueWatcher =
        new DialogueWatcher(
            client,
            textCleaner,
            new DialogueWidgetReader(client),
            synthesisDispatcher,
            prefetchCoordinator,
            audioService);
    publicChatSpeaker =
        new PublicChatSpeaker(client, textCleaner, synthesisDispatcher, config::voicePublicChat);

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
    dialogueWatcher = null;
    publicChatSpeaker = null;
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
    if (noticeManager == null || backendProvider == null || dialogueWatcher == null) {
      return;
    }
    noticeManager.maybeShowOnboarding();
    noticeManager.maybeWarnMissingCloudKey(backendProvider.active());
    dialogueWatcher.tick();
  }

  @Subscribe
  public void onChatMessage(ChatMessage event) {
    if (publicChatSpeaker == null) {
      return;
    }
    publicChatSpeaker.onChatMessage(event);
  }

  /**
   * Answers {@code ::voicedspend} with this session's billable cloud usage: lines voiced, lines
   * prefetched, characters actually sent, and what it cost (OpenRouter's billed figure, or a
   * labelled estimate where the provider reports only tokens), one chat line per provider used.
   * Cache hits cost nothing and are counted nowhere, so a session spent replaying known lines reads
   * as zero. The handler itself only snapshots counters on the client thread; the balance read runs
   * on the spend thread, and the readout is queued from there.
   */
  @Subscribe
  public void onCommandExecuted(CommandExecuted event) {
    if (!SpendReport.matches(event.getCommand())
        || spendTracker == null
        || noticeManager == null
        || usageClient == null
        || creditMeter == null) {
      return;
    }
    List<SpendTracker.ProviderSpend> snapshot = spendTracker.snapshot();
    String key = config.openRouterApiKey();
    // Captured now so the task holds its own references: shutDown nulls the fields, and the task
    // may still be running when it does.
    OpenRouterUsageClient reader = usageClient;
    OpenRouterCreditMeter meter = creditMeter;
    log.debug(
        "[TTS spend] ::{} received, {} provider(s) used", SpendReport.COMMAND, snapshot.size());
    submitSpendTask(
        () -> {
          try {
            // OpenRouter states what the key has spent; reading it is a network call, so it happens
            // off the game thread.
            Double spent = meter.spentSince(key, reader.fetchUsage(key));
            ChatNoticeManager notices = noticeManager;
            if (notices == null) {
              return;
            }
            // Queued rather than written directly, so the readout needs no hop back to the client
            // thread: the chat manager's queue is concurrent and Hooks.tick drains it every game
            // tick.
            List<String> lines = SpendReport.lines(snapshot, spent);
            for (String line : lines) {
              notices.postCommandResponse(line);
            }
            log.debug("[TTS spend] queued {} readout line(s), billedUsd={}", lines.size(), spent);
          } catch (RuntimeException e) {
            // An executor task that throws dies silently, which would leave the command looking
            // like it did nothing at all. A readout is never worth breaking the session over, so
            // the failure is logged and swallowed rather than propagated.
            log.warn("[TTS spend] could not build the spend readout", e);
          }
        });
  }

  /**
   * Reads the OpenRouter key's current all-time usage off the game thread and banks it as this
   * session's starting point. A no-op without a key; the {@link OpenRouterCreditMeter} ignores a
   * repeat for a key it already has a baseline for, so spend already made is never erased.
   */
  private void captureOpenRouterBaseline() {
    String key = config.openRouterApiKey();
    OpenRouterUsageClient reader = usageClient;
    OpenRouterCreditMeter meter = creditMeter;
    if (reader == null || meter == null || meter.hasBaselineFor(key)) {
      return;
    }
    submitSpendTask(() -> meter.recordBaseline(key, reader.fetchUsage(key)));
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
   * Records the provider a profile actually holds a key for, so a profile keyed only for OpenRouter
   * is never left voicing through a provider it cannot reach. Writes at most once per profile: the
   * write is itself the recorded choice, so {@link ProviderDefaultPolicy} declines to touch it
   * again.
   */
  void pinProviderWhenOnlyOpenRouterKeyed() {
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
   * Reacts to a backend-affecting config key changing at runtime: warms up the backend off the game
   * thread, so entering an OpenRouter key does the cloud connection handshake immediately rather
   * than starting cold on the next line, and re-baselines the spend meter on an OpenRouter key
   * change. The warm-up decision lives in {@link BackendWarmUpPolicy}; the work runs on the
   * pipeline thread via {@link DialogueAudioService#prewarm}. No-ops safely when the plugin is
   * disabled or mid-shutdown.
   */
  @Subscribe
  public void onConfigChanged(ConfigChanged event) {
    // An OpenRouter key swapped mid-session starts a different running total, so the old baseline
    // cannot be subtracted from the new key's usage. Checked ahead of the warm-up gate so the
    // re-baseline never depends on what happens to trigger a warm-up.
    if (VoicedDialogueConfig.GROUP.equals(event.getGroup())
        && VoicedDialogueConfig.OPENROUTER_API_KEY.equals(event.getKey())
        && creditMeter != null) {
      creditMeter.reset();
      captureOpenRouterBaseline();
    }
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
