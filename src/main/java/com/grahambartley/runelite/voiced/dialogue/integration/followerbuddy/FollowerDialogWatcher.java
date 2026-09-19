package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import com.grahambartley.runelite.voiced.dialogue.capture.DialogueTextCleaner;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class FollowerDialogWatcher {

  private final FollowerDialogReader reader;
  private final DialogueTextCleaner textCleaner;
  private final SynthesisDispatcher dispatcher;
  private final BooleanSupplier enabled;
  private final Supplier<NpcGender> gender;

  private String spokenText;
  private int spokenPage = -1;
  private volatile boolean onScreen;

  public FollowerDialogWatcher(
      FollowerDialogReader reader,
      DialogueTextCleaner textCleaner,
      SynthesisDispatcher dispatcher,
      BooleanSupplier enabled,
      Supplier<NpcGender> gender) {
    this.reader = reader;
    this.textCleaner = textCleaner;
    this.dispatcher = dispatcher;
    this.enabled = enabled;
    this.gender = gender;
  }

  public void tick() {
    if (!enabled.getAsBoolean()) {
      forget();
      return;
    }
    FollowerDialogLine line = reader.currentLine();
    onScreen = line != null;
    if (line == null) {
      forget();
      return;
    }
    if (line.page() == spokenPage && line.text().equals(spokenText)) {
      return;
    }
    spokenPage = line.page();
    spokenText = line.text();
    String cleaned = textCleaner.clean(line.text());
    if (cleaned.isEmpty()) {
      return;
    }
    if (line.playerSpeaking()) {
      dispatcher.speakPlayerDialogue(cleaned);
    } else {
      dispatcher.speakFollowerDialogue(cleaned, gender.get());
    }
  }

  public boolean isOnScreen() {
    return onScreen;
  }

  private void forget() {
    onScreen = false;
    spokenText = null;
    spokenPage = -1;
  }
}
