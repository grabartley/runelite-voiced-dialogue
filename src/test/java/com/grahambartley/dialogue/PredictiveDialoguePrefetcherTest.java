package com.grahambartley.dialogue;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

  @Test
  public void aTranscriptIsFetchedOncePerNpc() {
    when(wiki.lookup("Hans"))
        .thenReturn(
            WikiTranscript.parse(
                "* '''Hans:''' First.\n* '''Player:''' Second.\n* '''Hans:''' Third.", "Hans"));
    PredictiveDialoguePrefetcher predictive = predictive(Runnable::run);

    predictive.onLine("First.", "Hans");
    predictive.onLine("Second.", null);

    verify(wiki, times(1)).lookup("Hans");
  }

  @Test
  public void aFailedLookupIsRetriedOnTheNextLine() {
    when(wiki.lookup("Hans"))
        .thenReturn(null)
        .thenReturn(WikiTranscript.parse("* '''Hans:''' Hi.\n* '''Player:''' Bye.", "Hans"));
    PredictiveDialoguePrefetcher predictive = predictive(Runnable::run);

    predictive.onLine("Hi.", "Hans");
    predictive.onLine("Hi.", "Hans");

    verify(wiki, times(2)).lookup("Hans");
    verify(prefetcher).offer(any());
  }

  @Test
  public void predictiveSpendStopsAtFourLinesPerConversation() {
    when(wiki.lookup("Hans"))
        .thenReturn(
            WikiTranscript.parse(
                "* '''Hans:''' One.\n"
                    + "* '''Player:''' Two.\n"
                    + "* '''Hans:''' Three.\n"
                    + "* '''Player:''' Four.\n"
                    + "* '''Hans:''' Five.\n"
                    + "* '''Player:''' Six.\n"
                    + "* '''Hans:''' Seven.\n",
                "Hans"));
    PredictiveDialoguePrefetcher predictive = predictive(Runnable::run);

    predictive.onLine("One.", "Hans");
    predictive.onLine("Two.", null);
    predictive.onLine("Three.", null);
    predictive.onLine("Four.", null);
    predictive.onLine("Five.", null);
    predictive.onLine("Six.", null);

    ArgumentCaptor<List<SynthesisRequest>> offered = ArgumentCaptor.forClass(List.class);
    verify(prefetcher, atLeastOnce()).offer(offered.capture());
    int total = offered.getAllValues().stream().mapToInt(List::size).sum();
    assertEquals("the conversation budget is respected", 4, total);
  }

  @Test
  public void onlyAcceptedWorkCountsAgainstTheBudget() {
    // The shared prefetcher refuses everything (already warmed, or its own cap is full), so the
    // predictive budget must stay untouched rather than being spent on work that never happened.
    doReturn(0).when(prefetcher).offer(any());
    when(wiki.lookup("Hans"))
        .thenReturn(
            WikiTranscript.parse(
                "* '''Hans:''' One.\n"
                    + "* '''Player:''' Two.\n"
                    + "* '''Hans:''' Three.\n"
                    + "* '''Player:''' Four.\n"
                    + "* '''Hans:''' Five.\n"
                    + "* '''Player:''' Six.\n",
                "Hans"));
    PredictiveDialoguePrefetcher predictive = predictive(Runnable::run);

    predictive.onLine("One.", "Hans");
    predictive.onLine("Two.", null);
    predictive.onLine("Three.", null);
    predictive.onLine("Four.", null);
    predictive.onLine("Five.", null);

    verify(prefetcher, times(5)).offer(any());
  }

  @Test
  public void closingStopsAllFurtherPrediction() {
    when(wiki.lookup("Hans"))
        .thenReturn(WikiTranscript.parse("* '''Hans:''' Hi.\n* '''Player:''' Bye.", "Hans"));
    PredictiveDialoguePrefetcher predictive = predictive(Runnable::run);

    predictive.close();
    predictive.onLine("Hi.", "Hans");

    verify(wiki, never()).lookup(any());
    verify(prefetcher, never()).offer(any());
  }

  @Test
  public void anUnknownSpeakerIsNeverLookedUp() {
    predictive(Runnable::run).onLine("Hi.", "Unknown NPC");

    verify(wiki, never()).lookup(any());
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
