package com.grahambartley.runelite.voiced.dialogue.capture;

import static com.grahambartley.runelite.voiced.dialogue.capture.DialogueWidgetReader.isVisible;

import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

public final class NarrationWatcher {

  private static final int[] TEXT_WIDGETS = {
    InterfaceID.Objectbox.TEXT, InterfaceID.ObjectboxDouble.TEXT, InterfaceID.Messagebox.TEXT
  };

  private final Client client;
  private final DialogueTextCleaner textCleaner;
  private final SynthesisDispatcher dispatcher;
  private final BooleanSupplier enabled;

  private final String[] lastSpokenByWidget = new String[TEXT_WIDGETS.length];

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

  public boolean isOnScreen() {
    for (int widgetId : TEXT_WIDGETS) {
      if (isVisible(client.getWidget(widgetId))) {
        return true;
      }
    }
    return false;
  }

  public boolean tick() {
    if (!enabled.getAsBoolean()) {
      return false;
    }
    boolean open = false;
    for (int i = 0; i < TEXT_WIDGETS.length; i++) {
      Widget box = client.getWidget(TEXT_WIDGETS[i]);
      if (!isVisible(box)) {
        continue;
      }
      open = true;
      speakIfNew(i, box);
    }
    return open;
  }

  public void reset() {
    Arrays.fill(lastSpokenByWidget, null);
  }

  private void speakIfNew(int index, Widget box) {
    String text = box.getText();
    if (text == null || text.isEmpty() || text.equals(lastSpokenByWidget[index])) {
      return;
    }
    lastSpokenByWidget[index] = text;
    dispatcher.speakNarration(textCleaner.clean(text));
  }
}
