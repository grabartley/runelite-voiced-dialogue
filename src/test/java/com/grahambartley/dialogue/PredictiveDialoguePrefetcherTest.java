package com.grahambartley.dialogue;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.VoicedDialogueConfig;
import com.grahambartley.data.WikiTranscriptClient;
import com.grahambartley.synthesis.BackendProvider;
import com.grahambartley.synthesis.Emotion;
import com.grahambartley.synthesis.ProfanityFilter;
import com.grahambartley.synthesis.SynthesisBackend;
import com.grahambartley.synthesis.SynthesisRequest;
import com.grahambartley.synthesis.VoiceSpec;
import com.grahambartley.voice.ProfileResolver;
import com.grahambartley.voice.VoiceManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class PredictiveDialoguePrefetcherTest {

  private final WikiTranscriptClient wiki = mock(WikiTranscriptClient.class);
  private final ClientThread clientThread = mock(ClientThread.class);
  private final VoiceManager voices = mock(VoiceManager.class);
  private final ProfileResolver profiles = mock(ProfileResolver.class);
  private final DialoguePrefetcher prefetcher = mock(DialoguePrefetcher.class);
  private final BackendProvider backends = mock(BackendProvider.class);
  private final SynthesisBackend backend = mock(SynthesisBackend.class);
  private final VoicedDialogueConfig config = mock(VoicedDialogueConfig.class);

  @Before
  public void setUp() {
    when(config.predictiveWikiPrefetch()).thenReturn(true);
    when(config.prefetch()).thenReturn(true);
    when(backends.active()).thenReturn(backend);
    when(backend.isAvailable()).thenReturn(true);
    when(voices.resolveVoice(any(), any())).thenReturn(mock(VoiceSpec.class));
    when(prefetcher.offer(any()))
        .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
    doAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(0)).run();
              return true;
            })
        .when(clientThread)
        .invokeLater(any(Runnable.class));
  }

  @Test
  public void offersImmediateNpcSuccessorAsNeutral() {
    when(wiki.lookup("Hans"))
        .thenReturn(
            WikiTranscript.parse("* '''Player:''' Hi.\n* '''Hans:''' Hello there.", "Hans"));

    predictive(Runnable::run).onLine("Hi.", "Hans");

    ArgumentCaptor<List<SynthesisRequest>> offered = ArgumentCaptor.forClass(List.class);
    verify(prefetcher).offer(offered.capture());
    SynthesisRequest request = offered.getValue().get(0);
    assertEquals("Hello there.", request.text());
    assertEquals(Emotion.NEUTRAL, request.emotion());
    assertEquals(false, request.player());
  }

  @Test
  public void disabledToggleDoesNoWikiWork() {
    when(config.predictiveWikiPrefetch()).thenReturn(false);

    predictive(Runnable::run).onLine("Hi.", "Hans");

    verify(wiki, never()).lookup(any());
    verify(prefetcher, never()).offer(any());
  }

  @Test
  public void resetDropsLateWikiResultFromClosedConversation() {
    when(wiki.lookup("Hans"))
        .thenReturn(
            WikiTranscript.parse("* '''Player:''' Hi.\n* '''Hans:''' Hello there.", "Hans"));
    DeferredExecutor executor = new DeferredExecutor();
    PredictiveDialoguePrefetcher predictive = predictive(executor);

    predictive.onLine("Hi.", "Hans");
    predictive.reset();
    executor.runAll();

    verify(prefetcher, never()).offer(any());
  }

  @Test
  public void playerReplyUsesTheCurrentNpcTranscript() {
    when(wiki.lookup("Hans"))
        .thenReturn(
            WikiTranscript.parse(
                "* '''Hans:''' First.\n* '''Player:''' Second.\n* '''Hans:''' Third.", "Hans"));
    PredictiveDialoguePrefetcher predictive = predictive(Runnable::run);

    predictive.onLine("First.", "Hans");
    org.mockito.Mockito.clearInvocations(prefetcher);
    predictive.onLine("Second.", null);

    ArgumentCaptor<List<SynthesisRequest>> offered = ArgumentCaptor.forClass(List.class);
    verify(prefetcher).offer(offered.capture());
    assertEquals("Third.", offered.getValue().get(0).text());
  }

  private PredictiveDialoguePrefetcher predictive(Executor executor) {
    return new PredictiveDialoguePrefetcher(
        wiki,
        executor,
        clientThread,
        voices,
        profiles,
        new DialogueTextCleaner(new ProfanityFilter()),
        prefetcher,
        backends,
        config);
  }

  private static final class DeferredExecutor implements Executor {
    private final List<Runnable> tasks = new ArrayList<>();

    @Override
    public void execute(Runnable command) {
      tasks.add(command);
    }

    void runAll() {
      for (Runnable task : new ArrayList<>(tasks)) {
        task.run();
      }
      tasks.clear();
    }
  }
}
