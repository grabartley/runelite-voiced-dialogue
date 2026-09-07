package com.grahambartley.runelite.voiced.dialogue.capture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.profile.ProfanityFilter;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

/**
 * The per-tick narration scan: speaks each narration box once in the narrator voice, deduped per
 * box against the last text that box showed, reports whether one is open so the owner can drive its
 * interrupt edge, and goes fully silent when the toggle is off.
 */
public class NarrationWatcherTest {

  private final Client client = mock(Client.class);
  private final SynthesisDispatcher dispatcher = mock(SynthesisDispatcher.class);

  private boolean enabled = true;

  private final NarrationWatcher watcher =
      new NarrationWatcher(
          client, new DialogueTextCleaner(new ProfanityFilter()), dispatcher, () -> enabled);

  private Widget visibleWidget(String text) {
    Widget widget = mock(Widget.class);
    when(widget.isHidden()).thenReturn(false);
    when(widget.getText()).thenReturn(text);
    return widget;
  }

  private void showing(int widgetId, String text) {
    Widget box = visibleWidget(text);
    when(client.getWidget(widgetId)).thenReturn(box);
  }

  @Test
  public void anItemBoxIsNarratedOnceThenDeduped() {
    showing(InterfaceID.Objectbox.TEXT, "You find a key.");

    assertTrue("an open box reports the narration open", watcher.tick());
    watcher.tick();

    verify(dispatcher, times(1)).speakNarration("You find a key.");
  }

  @Test
  public void everyNarrationBoxKindIsNarrated() {
    showing(InterfaceID.Objectbox.TEXT, "You find a key.");
    showing(InterfaceID.ObjectboxDouble.TEXT, "You combine the halves.");
    showing(InterfaceID.Messagebox.TEXT, "The door creaks open.");

    watcher.tick();

    verify(dispatcher).speakNarration("You find a key.");
    verify(dispatcher).speakNarration("You combine the halves.");
    verify(dispatcher).speakNarration("The door creaks open.");
  }

  @Test
  public void eachBoxIsDedupedAgainstItsOwnLastLineOnly() {
    Widget objectbox = mock(Widget.class);
    when(objectbox.isHidden()).thenReturn(false);
    when(objectbox.getText()).thenReturn("You feel a chill.");
    when(client.getWidget(InterfaceID.Objectbox.TEXT)).thenReturn(objectbox);
    Widget messagebox = visibleWidget("You feel a chill.");
    when(client.getWidget(InterfaceID.Messagebox.TEXT)).thenReturn(null, messagebox);

    watcher.tick();
    watcher.tick();

    verify(dispatcher, times(2)).speakNarration("You feel a chill.");
  }

  @Test
  public void advancingAnItemBoxToItsNextLineNarratesTheNewLine() {
    Widget box = mock(Widget.class);
    when(box.isHidden()).thenReturn(false);
    when(box.getText()).thenReturn("You find a key.", "You find a rusty sword.");
    when(client.getWidget(InterfaceID.Objectbox.TEXT)).thenReturn(box);

    watcher.tick();
    watcher.tick();

    verify(dispatcher).speakNarration("You find a key.");
    verify(dispatcher).speakNarration("You find a rusty sword.");
  }

  @Test
  public void reopeningABoxAfterAResetNarratesItAgain() {
    showing(InterfaceID.Messagebox.TEXT, "The door creaks open.");

    watcher.tick();
    watcher.reset();
    watcher.tick();

    verify(dispatcher, times(2)).speakNarration("The door creaks open.");
  }

  @Test
  public void aHiddenOrAbsentBoxIsNeitherNarratedNorCountedAsOpen() {
    Widget hidden = mock(Widget.class);
    when(hidden.isHidden()).thenReturn(true);
    when(client.getWidget(InterfaceID.Objectbox.TEXT)).thenReturn(hidden);

    assertFalse("nothing on screen means nothing open", watcher.tick());

    verifyNoInteractions(dispatcher);
  }

  @Test
  public void anEmptyBoxIsNotNarrated() {
    showing(InterfaceID.Messagebox.TEXT, "");

    assertTrue("the box is still on screen", watcher.tick());

    verifyNoInteractions(dispatcher);
  }

  @Test
  public void markupIsStrippedBeforeNarrating() {
    showing(InterfaceID.Messagebox.TEXT, "<col=ffffff>The door creaks open.</col>");

    watcher.tick();

    verify(dispatcher).speakNarration("The door creaks open.");
  }

  @Test
  public void switchedOffItNarratesNothingAndReportsClosed() {
    enabled = false;
    showing(InterfaceID.Messagebox.TEXT, "The door creaks open.");

    assertFalse("an ignored box must not hold the dialogue open", watcher.tick());

    verify(dispatcher, never()).speakNarration("The door creaks open.");
  }

  @Test
  public void switchingItBackOnResumesNarrationWithoutARestart() {
    enabled = false;
    showing(InterfaceID.Messagebox.TEXT, "The door creaks open.");

    watcher.tick();
    enabled = true;
    watcher.tick();

    verify(dispatcher, times(1)).speakNarration("The door creaks open.");
  }
}
