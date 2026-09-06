package com.grahambartley.runelite.voiced.dialogue.capture;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
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
 * The per-tick dialogue scan: speaks a new NPC or player line once (deduped against the last spoken
 * text) and edge-triggers both the close interrupt and the prefetch reset only on the
 * open-&gt;closed transition, so idle ticks never truncate a playing public-chat clip nor churn the
 * prefetch session.
 */
@RunWith(JUnitParamsRunner.class)
public class DialogueWatcherTest {

  private final Client client = mock(Client.class);
  private final DialogueWidgetReader widgetReader = mock(DialogueWidgetReader.class);
  private final SynthesisDispatcher dispatcher = mock(SynthesisDispatcher.class);
  private final DialoguePrefetchCoordinator prefetchCoordinator =
      mock(DialoguePrefetchCoordinator.class);
  private final DialogueAudioService audioService = mock(DialogueAudioService.class);

  private final DialogueWatcher watcher =
      new DialogueWatcher(
          client,
          new DialogueTextCleaner(new ProfanityFilter()),
          widgetReader,
          dispatcher,
          prefetchCoordinator,
          audioService);

  @Before
  public void setUp() {
    when(widgetReader.currentNpcName()).thenReturn("Bob");
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
    Widget npc = mock(Widget.class);
    when(npc.isHidden()).thenReturn(false);
    when(npc.getText()).thenReturn("Greetings!");
    when(client.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(npc);

    watcher.tick();
    watcher.tick();

    verify(dispatcher, times(1))
        .speakDialogue(eq("Greetings!"), eq(Speaker.NPC), eq("Bob"), anyInt());
  }

  @Test
  public void aPlayerLineSpeaksAsThePlayerWithNoNpcName() {
    Widget player = mock(Widget.class);
    when(player.isHidden()).thenReturn(false);
    when(player.getText()).thenReturn("Hello there.");
    when(client.getWidget(InterfaceID.ChatRight.TEXT)).thenReturn(player);

    watcher.tick();

    verify(dispatcher).speakDialogue(eq("Hello there."), eq(Speaker.PLAYER), isNull(), anyInt());
  }

  @Test
  public void dialogueClosingInterruptsAudioAndResetsPrefetch() {
    Widget npc = mock(Widget.class);
    when(npc.isHidden()).thenReturn(false);
    when(npc.getText()).thenReturn("Greetings!");
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
}
