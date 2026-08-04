package com.grahambartley.dialogue;

import com.grahambartley.VoicedDialogueConfig;
import com.grahambartley.data.WikiTranscriptClient;
import com.grahambartley.synthesis.BackendProvider;
import com.grahambartley.synthesis.CharacterProfile;
import com.grahambartley.synthesis.Emotion;
import com.grahambartley.synthesis.SynthesisRequest;
import com.grahambartley.synthesis.VoiceSpec;
import com.grahambartley.voice.ProfileResolver;
import com.grahambartley.voice.VoiceManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;

/** Bounded one-step dialogue prediction from cached OSRS Wiki transcript pages. */
@Slf4j
public final class PredictiveDialoguePrefetcher {

  /** Reserves most of the shared ten-line cap for exact options visible in the client. */
  private static final int MAX_PREDICTIVE_PER_CONVERSATION = 4;

  /** Prevents long play sessions from retaining a transcript future for every NPC encountered. */
  private static final int MAX_CACHED_TRANSCRIPTS = 48;

  private final WikiTranscriptClient wiki;
  private final Executor executor;
  private final ClientThread clientThread;
  private final VoiceManager voiceManager;
  private final ProfileResolver profileResolver;
  private final DialogueTextCleaner textCleaner;
  private final DialoguePrefetcher prefetcher;
  private final BackendProvider backends;
  private final VoicedDialogueConfig config;
  private final ConcurrentHashMap<String, CompletableFuture<WikiTranscript>> transcripts =
      new ConcurrentHashMap<>();
  private final AtomicLong conversation = new AtomicLong();
  private final AtomicLong lineRevision = new AtomicLong();

  private String currentNpc;
  private int predictiveCount;
  private volatile boolean closed;

  public PredictiveDialoguePrefetcher(
      WikiTranscriptClient wiki,
      Executor executor,
      ClientThread clientThread,
      VoiceManager voiceManager,
      ProfileResolver profileResolver,
      DialogueTextCleaner textCleaner,
      DialoguePrefetcher prefetcher,
      BackendProvider backends,
      VoicedDialogueConfig config) {
    this.wiki = wiki;
    this.executor = executor;
    this.clientThread = clientThread;
    this.voiceManager = voiceManager;
    this.profileResolver = profileResolver;
    this.textCleaner = textCleaner;
    this.prefetcher = prefetcher;
    this.backends = backends;
    this.config = config;
  }

  /** Matches the current line and offers only its immediate transcript successors. */
  public void onLine(String text, String npcName) {
    if (closed
        || !config.predictiveWikiPrefetch()
        || !config.prefetch()
        || !backends.active().isAvailable()
        || text == null
        || text.isEmpty()) {
      return;
    }
    boolean currentLineIsNpc =
        npcName != null && !npcName.trim().isEmpty() && !"Unknown NPC".equals(npcName);
    if (currentLineIsNpc) {
      currentNpc = npcName.trim();
    }
    if (currentNpc == null) {
      return;
    }
    long expectedConversation = conversation.get();
    long expectedLine = lineRevision.incrementAndGet();
    String expectedNpc = currentNpc;
    String key = expectedNpc.toLowerCase(Locale.ROOT);
    CompletableFuture<WikiTranscript> transcript;
    try {
      transcript = transcriptFor(key, expectedNpc);
    } catch (RuntimeException e) {
      return;
    }
    CompletableFuture<WikiTranscript> fetched = transcript;
    transcript.whenComplete(
        (found, error) -> {
          if (found == null || error != null) {
            transcripts.remove(key, fetched);
          }
        });
    transcript.thenAccept(
        found ->
            clientThread.invokeLater(
                () -> {
                  if (closed
                      || found == null
                      || conversation.get() != expectedConversation
                      || lineRevision.get() != expectedLine
                      || !expectedNpc.equals(currentNpc)
                      || !config.predictiveWikiPrefetch()) {
                    return;
                  }
                  List<WikiTranscript.Line> successors = found.successors(text);
                  log.debug(
                      "[TTS predictive] npc='{}' matched={} successors={}",
                      expectedNpc,
                      !successors.isEmpty(),
                      successors.size());
                  offer(successors, expectedNpc);
                }));
  }

  public void reset() {
    conversation.incrementAndGet();
    lineRevision.incrementAndGet();
    currentNpc = null;
    predictiveCount = 0;
  }

  public void close() {
    closed = true;
    reset();
    transcripts.clear();
  }

  private CompletableFuture<WikiTranscript> transcriptFor(String key, String npcName) {
    CompletableFuture<WikiTranscript> cached = transcripts.get(key);
    if (cached != null) {
      return cached;
    }
    if (transcripts.size() >= MAX_CACHED_TRANSCRIPTS) {
      for (Map.Entry<String, CompletableFuture<WikiTranscript>> entry : transcripts.entrySet()) {
        if (!entry.getKey().equals(key) && transcripts.remove(entry.getKey(), entry.getValue())) {
          break;
        }
      }
    }
    return transcripts.computeIfAbsent(
        key, ignored -> CompletableFuture.supplyAsync(() -> wiki.lookup(npcName), executor));
  }

  private void offer(List<WikiTranscript.Line> lines, String npcName) {
    if (lines.isEmpty() || predictiveCount >= MAX_PREDICTIVE_PER_CONVERSATION) {
      return;
    }
    List<SynthesisRequest> requests = new ArrayList<>(lines.size());
    for (WikiTranscript.Line line : lines) {
      if (predictiveCount + requests.size() >= MAX_PREDICTIVE_PER_CONVERSATION) {
        break;
      }
      String cleaned = textCleaner.clean(line.text());
      if (cleaned.isEmpty()) {
        continue;
      }
      boolean player = line.speaker() == WikiTranscript.Speaker.PLAYER;
      String speaker = player ? VoiceManager.SPEAKER_PLAYER : VoiceManager.SPEAKER_NPC;
      String resolvedNpc = player ? null : npcName;
      VoiceSpec voice = voiceManager.resolveVoice(speaker, resolvedNpc);
      CharacterProfile profile = profileResolver.resolve(speaker, resolvedNpc);
      requests.add(
          new SynthesisRequest(
              cleaned, voice, Emotion.NEUTRAL, profile, /* skipTranslation= */ false, player));
    }
    // Only work the prefetcher actually accepted counts against the conversation budget, so lines
    // it deduplicated or refused do not quietly starve later predictions.
    predictiveCount += prefetcher.offer(requests);
    log.debug(
        "[TTS predictive] offering={} conversationUsed={}/{} npc='{}'",
        requests.size(),
        predictiveCount,
        MAX_PREDICTIVE_PER_CONVERSATION,
        npcName);
  }
}
