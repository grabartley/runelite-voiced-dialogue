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
import com.grahambartley.runelite.voiced.dialogue.capture.ExamineSpeaker;
import com.grahambartley.runelite.voiced.dialogue.capture.NarrationWatcher;
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
import com.grahambartley.runelite.voiced.dialogue.speech.ProviderDefaultPolicy;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import com.grahambartley.runelite.voiced.dialogue.speech.aistudio.AiStudioTtsBackend;
import com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterCreditMeter;
import com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterTtsBackend;
import com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterUsageClient;
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

@Slf4j
@PluginDescriptor(name = "Voiced Dialogue")
public class VoicedDialoguePlugin extends Plugin {

  private static final int CACHE_SIZE = 64;

  private static final int QUEUE_CAPACITY = 4;

  @Inject private Client client;

  @Inject private VoicedDialogueConfig config;

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
  private ExamineSpeaker examineSpeaker;

  private PublicChatSpeaker publicChatSpeaker;

  private SpendTracker spendTracker;

  private OpenRouterUsageClient usageClient;

  private OpenRouterCreditMeter creditMeter;

  private ExecutorService spendExecutor;

  @Override
  protected void startUp() {
    pinProviderWhenOnlyOpenRouterKeyed();
    VoiceManager voiceManager = VoiceManager.create(config, client);

    Path ttsDir = RuneLite.RUNELITE_DIR.toPath().resolve("voiced-dialogue");
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
    captureOpenRouterBaseline();

    OpenRouterTtsBackend openRouterBackend = new OpenRouterTtsBackend(okHttpClient, config, gson);
    openRouterBackend.setNotice(noticeManager::notifyFromBackendThread);
    openRouterBackend.setSpendTracker(spendTracker);
    AiStudioTtsBackend aiStudioBackend = new AiStudioTtsBackend(okHttpClient, config, gson);
    aiStudioBackend.setNotice(noticeManager::notifyFromBackendThread);
    aiStudioBackend.setSpendTracker(spendTracker);
    backendProvider = new BackendProvider(openRouterBackend, aiStudioBackend, config::ttsProvider);
    DiskAudioCache diskCache =
        new DiskAudioCache(ttsDir.resolve("cache"), config.cacheSizeLimitMiB() * 1024L * 1024);
    audioService =
        new DialogueAudioService(
            backendProvider,
            new StreamingAudioPlayer(),
            diskCache,
            CACHE_SIZE,
            QUEUE_CAPACITY,
            config::volume);
    audioService.prewarm(backendProvider::warmUpActive);
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
            audioService,
            new NarrationWatcher(client, textCleaner, synthesisDispatcher, config::voiceNarration));
    publicChatSpeaker =
        new PublicChatSpeaker(client, textCleaner, synthesisDispatcher, config::voicePublicChat);
    examineSpeaker =
        new ExamineSpeaker(
            textCleaner,
            synthesisDispatcher,
            config::voiceExamineText,
            dialogueWatcher::isDialogueOpen,
            clientThread::invokeLater);

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
    examineSpeaker = null;
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
    if (publicChatSpeaker != null) {
      publicChatSpeaker.onChatMessage(event);
    }
    if (examineSpeaker != null) {
      examineSpeaker.onChatMessage(event);
    }
  }

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
    OpenRouterUsageClient reader = usageClient;
    OpenRouterCreditMeter meter = creditMeter;
    log.debug(
        "[TTS spend] ::{} received, {} provider(s) used", SpendReport.COMMAND, snapshot.size());
    submitSpendTask(
        () -> {
          try {
            Double spent = meter.spentSince(key, reader.fetchUsage(key));
            ChatNoticeManager notices = noticeManager;
            if (notices == null) {
              return;
            }
            List<String> lines = SpendReport.lines(snapshot, spent);
            for (String line : lines) {
              notices.postCommandResponse(line);
            }
            log.debug("[TTS spend] queued {} readout line(s), billedUsd={}", lines.size(), spent);
          } catch (RuntimeException e) {
            log.warn("[TTS spend] could not build the spend readout", e);
          }
        });
  }

  private void captureOpenRouterBaseline() {
    String key = config.openRouterApiKey();
    OpenRouterUsageClient reader = usageClient;
    OpenRouterCreditMeter meter = creditMeter;
    if (reader == null || meter == null || meter.hasBaselineFor(key)) {
      return;
    }
    submitSpendTask(() -> meter.recordBaseline(key, reader.fetchUsage(key)));
  }

  private void submitSpendTask(Runnable task) {
    ExecutorService executor = spendExecutor;
    if (executor == null) {
      return;
    }
    try {
      executor.execute(task);
    } catch (RejectedExecutionException ignored) {
    }
  }

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

  @Subscribe
  public void onConfigChanged(ConfigChanged event) {
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
    backendProvider.clearRateLimits();
    audioService.prewarm(backendProvider::warmUpActive);
  }

  @Provides
  VoicedDialogueConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(VoicedDialogueConfig.class);
  }
}
