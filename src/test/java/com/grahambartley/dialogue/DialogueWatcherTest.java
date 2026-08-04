package com.grahambartley.dialogue;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.synthesis.ProfanityFilter;
import com.grahambartley.synthesis.SynthesisDispatcher;
import com.grahambartley.tts.DialogueAudioService;
import com.grahambartley.voice.VoiceManager;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The per-tick dialogue scan: speaks a new NPC or player line once the widgets are stable, waits
 * briefly for a lagging chat-head expression, and cuts audio on a debounced close without ever
 * interrupting a newer line.
 */
@RunWith(JUnitParamsRunner.class)
public class DialogueWatcherTest {

  private final Client client = mock(Client.class);
  private final DialogueWidgetReader widgetReader = mock(DialogueWidgetReader.class);
  private final SynthesisDispatcher dispatcher = mock(SynthesisDispatcher.class);
  private final DialoguePrefetchCoordinator prefetchCoordinator =
      mock(DialoguePrefetchCoordinator.class);
  private final DialoguePrefetcher prefetcher = mock(DialoguePrefetcher.class);
  private final DialogueAudioService audioService = mock(DialogueAudioService.class);

  private final DialogueWatcher watcher =
      new DialogueWatcher(
          client,
          new DialogueTextCleaner(new ProfanityFilter()),
          widgetReader,
          dispatcher,
          prefetchCoordinator,
          prefetcher,
          audioService);

  @Before
  public void setUp() {
    when(widgetReader.currentNpcName()).thenReturn("Bob");
    when(dispatcher.speakDialogue(any(), any(), any(), anyInt())).thenReturn(true);
  }

  private Object[] interruptOnCloseCases() {
    return new Object[] {
      // a sustained close reaches the debounce threshold
      new Object[] {false, true, DialogueWatcher.CLOSE_DEBOUNCE_CLIENT_TICKS, true},
      // transient client-tick gaps never cut audio
      new Object[] {false, true, 1, false},
      new Object[] {false, true, DialogueWatcher.CLOSE_DEBOUNCE_CLIENT_TICKS - 1, false},
      // still idle (was closed, still closed) -> never interrupt, so public chat plays on
      new Object[] {false, false, DialogueWatcher.CLOSE_DEBOUNCE_CLIENT_TICKS, false},
      // dialogue still open -> nothing to interrupt
      new Object[] {true, true, 0, false},
      // dialogue just opened -> nothing to interrupt
      new Object[] {true, false, 0, false},
    };
  }

  @Test
  @Parameters(method = "interruptOnCloseCases")
  public void interruptDecisionFiresOnlyOnASustainedClose(
      boolean dialogueOpen, boolean wasDialogueOpen, int closedTicks, boolean expected) {
    assertEquals(
        expected,
        DialogueWatcher.shouldInterruptOnClose(dialogueOpen, wasDialogueOpen, closedTicks));
  }

  @Test
  public void newNpcLineIsSpokenOnceTheWidgetsAreStableThenDeduped() {
    Widget npc = mock(Widget.class);
    when(npc.isHidden()).thenReturn(false);
    when(npc.getText()).thenReturn("Greetings!");
    when(client.getWidget(ComponentID.DIALOG_NPC_TEXT)).thenReturn(npc);

    watcher.tick();
    watcher.tick();
    watcher.tick();

    verify(dispatcher, times(1))
        .speakDialogue(eq("Greetings!"), eq(VoiceManager.SPEAKER_NPC), eq("Bob"), anyInt());
    verify(prefetcher, times(1)).advanceNode();
  }

  @Test
  public void oneTickOfUnstableWidgetsDoesNotSpeakYet() {
    Widget npc = mock(Widget.class);
    when(npc.isHidden()).thenReturn(false);
    when(npc.getText()).thenReturn("Greetings!");
    when(client.getWidget(ComponentID.DIALOG_NPC_TEXT)).thenReturn(npc);

    watcher.tick();

    verify(dispatcher, never()).speakDialogue(any(), any(), any(), anyInt());
  }

  @Test
  public void sustainedCloseInterruptsTheDispatchedLineAndResetsPrefetch() {
    Widget npc = mock(Widget.class);
    when(npc.isHidden()).thenReturn(false);
    when(npc.getText()).thenReturn("Greetings!");
    when(client.getWidget(ComponentID.DIALOG_NPC_TEXT)).thenReturn(npc, npc, (Widget) null);
    when(audioService.currentEpoch()).thenReturn(4L);

    watcher.tick();
    watcher.tick();
    for (int i = 0; i < DialogueWatcher.CLOSE_DEBOUNCE_CLIENT_TICKS; i++) {
      watcher.tick();
    }

    verify(audioService, times(1)).interruptIfCurrent(4L);
    verify(prefetcher).reset();
  }

  @Test
  public void transientWidgetGapDoesNotInterruptDialogueAudio() {
    Widget npc = mock(Widget.class);
    when(npc.isHidden()).thenReturn(false);
    when(npc.getText()).thenReturn("Greetings!");
    when(client.getWidget(ComponentID.DIALOG_NPC_TEXT)).thenReturn(npc, npc, null, npc);

    watcher.tick();
    watcher.tick();
    watcher.tick();
    watcher.tick();

    verify(audioService, never()).interruptIfCurrent(anyLong());
    verify(audioService, never()).interrupt();
  }

  @Test
  public void unchangedOptionsAreProcessedOnlyOnceAcrossClientTicks() {
    Widget options = mock(Widget.class);
    Widget option = mock(Widget.class);
    when(options.isHidden()).thenReturn(false);
    when(options.getDynamicChildren()).thenReturn(new Widget[] {option});
    when(option.getText()).thenReturn("Ask about the quest");
    when(client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS)).thenReturn(options);

    watcher.tick();
    watcher.tick();

    verify(prefetchCoordinator, times(1)).prefetchOptions(options);
  }

  @Test
  public void waitsForALaggingChatHeadBeforeVoicingTheLine() {
    Widget npc = mock(Widget.class);
    when(npc.isHidden()).thenReturn(false);
    when(npc.getText()).thenReturn("Halt!");
    when(client.getWidget(ComponentID.DIALOG_NPC_TEXT)).thenReturn(npc);
    when(widgetReader.hasHead(anyInt())).thenReturn(true);
    when(widgetReader.headAnimationId(anyInt())).thenReturn(-1, -1, 614, 614);

    watcher.tick();
    watcher.tick();
    watcher.tick();
    watcher.tick();

    verify(dispatcher, times(1)).speakDialogue("Halt!", VoiceManager.SPEAKER_NPC, "Bob", 614);
    verify(dispatcher, never()).speakDialogue("Halt!", VoiceManager.SPEAKER_NPC, "Bob", -1);
  }

  @Test
  public void headlessDialogueStillSpeaksAsNeutral() {
    Widget npc = mock(Widget.class);
    when(npc.isHidden()).thenReturn(false);
    when(npc.getText()).thenReturn("A dusty tome.");
    when(client.getWidget(ComponentID.DIALOG_NPC_TEXT)).thenReturn(npc);
    when(widgetReader.hasHead(anyInt())).thenReturn(false);
    when(widgetReader.headAnimationId(anyInt())).thenReturn(-1);

    watcher.tick();
    watcher.tick();

    verify(dispatcher, times(1))
        .speakDialogue("A dusty tome.", VoiceManager.SPEAKER_NPC, "Bob", -1);
  }

  @Test
  public void changingNpcResetsThePrefetchSessionWithoutWaitingForClose() {
    Widget first = mock(Widget.class);
    when(first.isHidden()).thenReturn(false);
    when(first.getText()).thenReturn("First");
    Widget second = mock(Widget.class);
    when(second.isHidden()).thenReturn(false);
    when(second.getText()).thenReturn("Second");
    when(client.getWidget(ComponentID.DIALOG_NPC_TEXT)).thenReturn(first, first, second, second);
    when(widgetReader.currentNpcName()).thenReturn("Alice", "Alice", "Bob", "Bob");

    watcher.tick();
    watcher.tick();
    watcher.tick();
    watcher.tick();

    verify(prefetcher).reset();
  }

  @Test
  public void anUnvoicedLineStopsThePreviousDialogueAudio() {
    Widget first = mock(Widget.class);
    when(first.isHidden()).thenReturn(false);
    when(first.getText()).thenReturn("First");
    Widget second = mock(Widget.class);
    when(second.isHidden()).thenReturn(false);
    when(second.getText()).thenReturn("Second");
    when(client.getWidget(ComponentID.DIALOG_NPC_TEXT)).thenReturn(first, first, second, second);
    when(audioService.currentEpoch()).thenReturn(7L);
    when(dispatcher.speakDialogue(eq("First"), any(), any(), anyInt())).thenReturn(true);
    when(dispatcher.speakDialogue(eq("Second"), any(), any(), anyInt())).thenReturn(false);

    watcher.tick();
    watcher.tick();
    watcher.tick();
    watcher.tick();

    verify(audioService).interruptIfCurrent(7L);
  }
}
