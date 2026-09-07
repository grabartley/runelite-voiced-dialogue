package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Scans the narration boxes each game tick and speaks new text in the narrator voice: the item box
 * ("You find a key."), the two-model box, and the plain message box quests use for their narrative
 * beats. None of these carries a chat head or a speaker name, so they are the game narrating rather
 * than a character talking.
 *
 * <p>Owned by {@link DialogueWatcher}, which folds the returned open state into the same
 * open-&gt;closed edge the chat widgets use, so a narration box cuts its audio when it closes and
 * advancing through a run of boxes interrupts the previous one. Reads the client only on the game
 * thread.
 */
public final class NarrationWatcher {

  /** The narration text widgets, scanned in the order the client stacks them. */
  private static final int[] TEXT_WIDGETS = {
    InterfaceID.Objectbox.TEXT, InterfaceID.ObjectboxDouble.TEXT, InterfaceID.Messagebox.TEXT
  };

  private final Client client;
  private final DialogueTextCleaner textCleaner;
  private final SynthesisDispatcher dispatcher;
  private final BooleanSupplier enabled;

  private final Map<Integer, String> lastSpokenByWidget = new HashMap<>();

  public NarrationWatcher(
      Client client,
      DialogueTextCleaner textCleaner,
      SynthesisDispatcher dispatcher,
      BooleanSupplier enabled) {
    this.client = client;
    this.textCleaner = textCleaner;
    this.dispatcher = dispatcher;
    this.enabled = enabled;
  }

  /**
   * Speaks any narration box showing text it has not already spoken, and reports whether one is
   * open. Switched off, it speaks nothing and reports closed, so the caller's interrupt edge
   * behaves exactly as it does with no narration in the game at all.
   */
  public boolean tick() {
    if (!enabled.getAsBoolean()) {
      return false;
    }
    boolean open = false;
    for (int widgetId : TEXT_WIDGETS) {
      Widget box = client.getWidget(widgetId);
      if (box == null || box.isHidden()) {
        continue;
      }
      open = true;
      speakIfNew(widgetId, box);
    }
    return open;
  }

  /** Forgets what each box last said, so reopening one narrates it again. */
  public void reset() {
    lastSpokenByWidget.clear();
  }

  private void speakIfNew(int widgetId, Widget box) {
    String text = box.getText();
    if (text == null || text.isEmpty() || text.equals(lastSpokenByWidget.get(widgetId))) {
      return;
    }
    lastSpokenByWidget.put(widgetId, text);
    dispatcher.speakNarration(textCleaner.clean(text));
  }
}
