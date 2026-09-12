package com.grahambartley.runelite.voiced.dialogue.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
      new Object[] {false, true, true},
      new Object[] {false, false, false},
      new Object[] {true, true, false},
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
  public void dialogueOpenStateIsReportedForTheFeaturesThatMustYieldToIt() {
    Widget npc = visibleWidget("Greetings!");
    when(client.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(npc, (Widget) null);

    assertFalse("nothing has been scanned yet", watcher.isDialogueOpen());
    watcher.tick();
    assertTrue("a visible dialogue reports open", watcher.isDialogueOpen());
    watcher.tick();
    assertFalse("a closed dialogue reports closed", watcher.isDialogueOpen());
  }

  @Test
  public void anOpenNarrationBoxHoldsTheDialogueOpenSoNothingIsInterrupted() {
    when(narrationWatcher.tick()).thenReturn(true);

    watcher.tick();
    assertTrue("narration holds the audio channel too", watcher.isDialogueOpen());
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

  @Test
  public void aDialogueOpeningBetweenTicksIsAlreadyOnScreenBeforeTheScanCatchesUp() {
    Widget npc = visibleWidget("Greetings!");
    when(client.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(npc);

    assertFalse("the settled state still trails the scan", watcher.isDialogueOpen());
    assertTrue("the live read sees the box the moment it opens", watcher.isConversationOnScreen());
  }

  @Test
  public void aPlayerDialogueOpeningBetweenTicksIsAlsoSeenLive() {
    Widget player = visibleWidget("Hello!");
    when(client.getWidget(InterfaceID.ChatRight.TEXT)).thenReturn(player);

    assertTrue(watcher.isConversationOnScreen());
  }

  @Test
  public void anOpenOptionListCountsAsAConversationOnScreen() {
    Widget options = mock(Widget.class);
    when(options.isHidden()).thenReturn(false);
    when(client.getWidget(InterfaceID.Chatmenu.OPTIONS)).thenReturn(options);

    assertFalse("the settled state tracks dialogue boxes only", watcher.isDialogueOpen());
    assertTrue(
        "picking an option is still being in a conversation", watcher.isConversationOnScreen());
  }

  @Test
  public void aHiddenDialogueBoxIsNotOnScreen() {
    Widget hidden = mock(Widget.class);
    when(hidden.isHidden()).thenReturn(true);
    when(client.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(hidden);

    assertFalse(watcher.isConversationOnScreen());
  }

  @Test
  public void anOpenNarrationBoxKeepsTheLiveReadOpenUntilItCloses() {
    when(narrationWatcher.tick()).thenReturn(true);
    watcher.tick();

    assertTrue(
        "narration holds the channel for the live read too", watcher.isConversationOnScreen());
  }
}
