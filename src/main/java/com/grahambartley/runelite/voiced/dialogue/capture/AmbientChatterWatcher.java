package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.speech.DialogueAudioService;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.OverheadTextChanged;

public final class AmbientChatterWatcher {

  private final Client client;
  private final DialogueTextCleaner textCleaner;
  private final SynthesisDispatcher dispatcher;
  private final BooleanSupplier enabled;
  private final BooleanSupplier conversationOnScreen;
  private final IntSupplier volume;

  public AmbientChatterWatcher(
      Client client,
      DialogueTextCleaner textCleaner,
      SynthesisDispatcher dispatcher,
      BooleanSupplier enabled,
      BooleanSupplier conversationOnScreen,
      IntSupplier volume) {
    this.client = client;
    this.textCleaner = textCleaner;
    this.dispatcher = dispatcher;
    this.enabled = enabled;
    this.conversationOnScreen = conversationOnScreen;
    this.volume = volume;
  }

  public void onOverheadTextChanged(OverheadTextChanged event) {
    if (!enabled.getAsBoolean()) {
      return;
    }
    Actor actor = event.getActor();
    if (!(actor instanceof NPC)) {
      return;
    }
    NPC npc = (NPC) actor;
    String overheadText = event.getOverheadText();
    if (overheadText == null
        || !AmbientEarshot.isWithinEarshot(distanceTo(npc))
        || conversationOnScreen.getAsBoolean()) {
      return;
    }
    String cleaned = textCleaner.clean(overheadText);
    if (cleaned.isEmpty()) {
      return;
    }
    dispatcher.speakAmbient(cleaned, npc, () -> volumeFor(npc));
  }

  private int volumeFor(NPC npc) {
    int distance = distanceTo(npc);
    if (!AmbientEarshot.isWithinEarshot(distance)) {
      return DialogueAudioService.AMBIENT_OUT_OF_EARSHOT;
    }
    return AmbientEarshot.volumeAt(volume.getAsInt(), distance);
  }

  private int distanceTo(NPC npc) {
    Player local = client.getLocalPlayer();
    if (local == null) {
      return Integer.MAX_VALUE;
    }
    WorldPoint listener = local.getWorldLocation();
    WorldPoint speaker = npc.getWorldLocation();
    if (listener == null || speaker == null) {
      return Integer.MAX_VALUE;
    }
    return listener.distanceTo(speaker);
  }
}
