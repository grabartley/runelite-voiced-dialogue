package com.grahambartley.runelite.voiced.dialogue.capture;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.profile.ProfanityFilter;
import com.grahambartley.runelite.voiced.dialogue.profile.Speaker;
import com.grahambartley.runelite.voiced.dialogue.speech.DialogueAudioService;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The per-tick dialogue scan: speaks a new NPC or player line once (deduped per speaker against the
 * last text that speaker said) and edge-triggers both the close interrupt and the prefetch reset
 * only on the open-&gt;closed transition, so idle ticks never truncate a playing public-chat clip
 * nor churn the prefetch session.
 */
@RunWith(JUnitParamsRunner.class)
public class DialogueWatcherTest {

  private final Client client = mock(Client.class);
  private final DialogueWidgetReader widgetReader = mock(DialogueWidgetReader.class);
  private final SynthesisDispatcher dispatcher = mock(SynthesisDispatcher.class);
  private final DialoguePrefetchCoordinator prefetchCoordinator =
      mock(DialoguePrefetchCoordinator.class);
  private final DialogueAudioService audioService = mock(DialogueAudioService.class);
  private final NarrationWatcher narrationWatcher = mock(NarrationWatcher.class);

  private final DialogueWatcher watcher =
      new DialogueWatcher(
          client,
          new DialogueTextCleaner(new ProfanityFilter()),
          widgetReader,
          dispatcher,
          prefetchCoordinator,
          audioService,
          narrationWatcher);

  @Before
  public void setUp() {
    when(widgetReader.currentNpcName()).thenReturn("Bob");
  }

  private Widget visibleWidget(String text) {
    Widget widget = mock(Widget.class);
    when(widget.isHidden()).thenReturn(false);
    when(widget.getText()).thenReturn(text);
    return widget;
  }

  private Object[] interruptOnCloseCases() {
    return new Object[] {
      // dialogue just closed -> cut its audio once
      new Object[] {false, true, true},
      // still idle (was closed, still closed) -> never interrupt, so public chat plays on
      new Object[] {false, false, false},
      // dialogue still open -> nothing to interrupt
      new Object[] {true, true, false},
      // dialogue just opened -> nothing to interrupt
      new Object[] {true, false, false},
    };
  }

  @Test
  @Parameters(method = "interruptOnCloseCases")
  public void interruptDecisionFiresOnlyOnTheOpenToClosedTransition(
      boolean dialogueOpen, boolean wasDialogueOpen, boolean expected) {
    assertEquals(expected, DialogueWatcher.shouldInterruptOnClose(dialogueOpen, wasDialogueOpen));
  }

  @Test
  public void newNpcLineIsSpokenOnceThenDeduped() {
    Widget npc = visibleWidget("Greetings!");
    when(client.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(npc);

    watcher.tick();
    watcher.tick();

    verify(dispatcher, times(1))
        .speakDialogue(eq("Greetings!"), eq(Speaker.NPC), eq("Bob"), anyInt());
  }

  @Test
  public void newPlayerLineIsSpokenOnceThenDeduped() {
    Widget player = visibleWidget("Hello there.");
    when(client.getWidget(InterfaceID.ChatRight.TEXT)).thenReturn(player);

    watcher.tick();
    watcher.tick();

    verify(dispatcher, times(1))
        .speakDialogue(eq("Hello there."), eq(Speaker.PLAYER), isNull(), anyInt());
  }

  @Test
  public void anNpcLineFollowedByAnIdenticalPlayerLineSpeaksBoth() {
    Widget npc = visibleWidget("Yes.");
    Widget player = visibleWidget("Yes.");
    when(client.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(npc, (Widget) null);
    when(client.getWidget(InterfaceID.ChatRight.TEXT)).thenReturn((Widget) null, player);

    watcher.tick();
    watcher.tick();

    verify(dispatcher).speakDialogue(eq("Yes."), eq(Speaker.NPC), eq("Bob"), anyInt());
    verify(dispatcher).speakDialogue(eq("Yes."), eq(Speaker.PLAYER), isNull(), anyInt());
  }

  @Test
  public void aPlayerLineFollowedByAnIdenticalNpcLineSpeaksBoth() {
    Widget player = visibleWidget("Okay.");
    Widget npc = visibleWidget("Okay.");
    when(client.getWidget(InterfaceID.ChatRight.TEXT)).thenReturn(player, (Widget) null);
    when(client.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(null, npc);

    watcher.tick();
    watcher.tick();

    verify(dispatcher).speakDialogue(eq("Okay."), eq(Speaker.PLAYER), isNull(), anyInt());
    verify(dispatcher).speakDialogue(eq("Okay."), eq(Speaker.NPC), eq("Bob"), anyInt());
  }

  @Test
  public void aSpeakerRepeatingItsOwnLineAfterTheOtherSpeakerStaysDeduped() {
    Widget npc = visibleWidget("Yes.");
    Widget player = visibleWidget("Hello.");
    when(client.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(npc, null, npc);
    when(client.getWidget(InterfaceID.ChatRight.TEXT)).thenReturn(null, player, null);

    watcher.tick();
    watcher.tick();
    watcher.tick();

    verify(dispatcher).speakDialogue(eq("Hello."), eq(Speaker.PLAYER), isNull(), anyInt());
    verify(dispatcher, times(1)).speakDialogue(eq("Yes."), eq(Speaker.NPC), eq("Bob"), anyInt());
  }

  @Test
  public void reopenedDialogueRepeatsBothSpeakersLinesAfterTheCloseResetsThem() {
    Widget npc = visibleWidget("Greetings!");
    Widget player = visibleWidget("Yes.");
    // One side renders per tick: NPC, player, fully closed, then the same conversation again.
    when(client.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(npc, null, null, npc, null);
    when(client.getWidget(InterfaceID.ChatRight.TEXT)).thenReturn(null, player, null, null, player);

    for (int tick = 0; tick < 5; tick++) {
      watcher.tick();
    }

    verify(dispatcher, times(2))
        .speakDialogue(eq("Greetings!"), eq(Speaker.NPC), eq("Bob"), anyInt());
    verify(dispatcher, times(2)).speakDialogue(eq("Yes."), eq(Speaker.PLAYER), isNull(), anyInt());
  }

  @Test
  public void dialogueClosingInterruptsAudioAndResetsPrefetch() {
    Widget npc = visibleWidget("Greetings!");
    // Open on the first tick, gone on the second.
    when(client.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(npc, (Widget) null);

    watcher.tick();
    watcher.tick();

    verify(audioService, times(1)).interrupt();
    verify(prefetchCoordinator, times(1)).reset();
  }

  @Test
  public void idleTicksResetPrefetchOnlyOnTheFirstClosedTick() {
    watcher.tick();
    watcher.tick();
    watcher.tick();

    verify(prefetchCoordinator, times(1)).reset();
  }

  @Test
  public void anOpenNarrationBoxHoldsTheDialogueOpenSoNothingIsInterrupted() {
    when(narrationWatcher.tick()).thenReturn(true);

    watcher.tick();
    watcher.tick();

    verify(audioService, never()).interrupt();
    verify(prefetchCoordinator, never()).reset();
  }

  @Test
  public void aClosingNarrationBoxCutsItsAudioAndForgetsWhatItNarrated() {
    when(narrationWatcher.tick()).thenReturn(true, false);

    watcher.tick();
    watcher.tick();

    verify(audioService, times(1)).interrupt();
    verify(narrationWatcher, times(1)).reset();
  }

  @Test
  public void aClosingDialogueForgetsWhatTheNarrationBoxesSaid() {
    Widget npc = visibleWidget("Greetings!");
    when(client.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(npc, (Widget) null);

    watcher.tick();
    watcher.tick();

    verify(narrationWatcher, times(1)).reset();
  }
}
