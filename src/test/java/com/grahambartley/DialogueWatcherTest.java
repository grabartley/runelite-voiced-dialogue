package com.grahambartley;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.synthesis.ProfanityFilter;
import com.grahambartley.tts.DialogueAudioService;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import org.junit.Before;
import org.junit.Test;

/**
 * The per-tick dialogue scan: speaks a new NPC or player line once (deduped against the last spoken
 * text) and edge-triggers the close interrupt only on the open-&gt;closed transition, so idle ticks
 * never truncate a playing public-chat clip.
 */
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
  }

  @Test
  public void interruptDecisionFiresOnlyOnTheOpenToClosedTransition() {
    assertTrue(
        "dialogue just closed -> cut its audio once",
        DialogueWatcher.shouldInterruptOnClose(false, true));
    assertFalse(
        "still idle (was closed, still closed) -> never interrupt, so public chat plays on",
        DialogueWatcher.shouldInterruptOnClose(false, false));
    assertFalse(
        "dialogue still open -> nothing to interrupt",
        DialogueWatcher.shouldInterruptOnClose(true, true));
    assertFalse(
        "dialogue just opened -> nothing to interrupt",
        DialogueWatcher.shouldInterruptOnClose(true, false));
  }

  @Test
  public void newNpcLineIsSpokenOnceThenDeduped() {
    Widget npc = mock(Widget.class);
    when(npc.isHidden()).thenReturn(false);
    when(npc.getText()).thenReturn("Greetings!");
    when(client.getWidget(WidgetInfo.DIALOG_NPC_TEXT)).thenReturn(npc);

    watcher.tick();
    watcher.tick();

    verify(dispatcher, times(1))
        .speakDialogue(eq("Greetings!"), eq(VoiceManager.SPEAKER_NPC), eq("Bob"), anyInt());
  }

  @Test
  public void dialogueClosingInterruptsAudioAndResetsPrefetch() {
    Widget npc = mock(Widget.class);
    when(npc.isHidden()).thenReturn(false);
    when(npc.getText()).thenReturn("Greetings!");
    // Open on the first tick, gone on the second.
    when(client.getWidget(WidgetInfo.DIALOG_NPC_TEXT)).thenReturn(npc, (Widget) null);

    watcher.tick();
    watcher.tick();

    verify(audioService, times(1)).interrupt();
    verify(prefetcher).reset();
  }
}
