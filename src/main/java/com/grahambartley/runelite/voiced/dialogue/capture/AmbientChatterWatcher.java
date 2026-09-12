package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.cache.LruCache;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.OverheadTextChanged;

public final class AmbientChatterWatcher {

  static final int EARSHOT_TILES = 7;

  static final long NPC_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(10);

  static final long MAX_SLOT_HOLD_NANOS = TimeUnit.SECONDS.toNanos(30);

  private static final int TRACKED_NPC_LIMIT = 64;

  private final Client client;
  private final DialogueTextCleaner textCleaner;
  private final SynthesisDispatcher dispatcher;
  private final BooleanSupplier enabled;
  private final BooleanSupplier dialogueOpen;
  private final LongSupplier clock;

  private final LruCache<Integer, Long> spokenAtByNpcIndex = new LruCache<>(TRACKED_NPC_LIMIT);
  private final AtomicReference<Slot> slot = new AtomicReference<>();

  public AmbientChatterWatcher(
      Client client,
      DialogueTextCleaner textCleaner,
      SynthesisDispatcher dispatcher,
      BooleanSupplier enabled,
      BooleanSupplier dialogueOpen,
      LongSupplier clock) {
    this.client = client;
    this.textCleaner = textCleaner;
    this.dispatcher = dispatcher;
    this.enabled = enabled;
    this.dialogueOpen = dialogueOpen;
    this.clock = clock;
  }

  public void onOverheadTextChanged(OverheadTextChanged event) {
    if (!enabled.getAsBoolean() || dialogueOpen.getAsBoolean()) {
      return;
    }
    Actor actor = event.getActor();
    if (!(actor instanceof NPC)) {
      return;
    }
    NPC npc = (NPC) actor;
    String overheadText = event.getOverheadText();
    if (overheadText == null || !isWithinEarshot(npc)) {
      return;
    }
    String cleaned = textCleaner.clean(overheadText);
    if (cleaned.isEmpty()) {
      return;
    }
    long now = clock.getAsLong();
    if (isOnCooldown(npc, now) || isSlotHeld(now)) {
      return;
    }
    spokenAtByNpcIndex.put(npc.getIndex(), now);
    Slot mine = new Slot(now + MAX_SLOT_HOLD_NANOS);
    slot.set(mine);
    dispatcher.speakAmbient(cleaned, npc, () -> slot.compareAndSet(mine, null));
  }

  private boolean isSlotHeld(long now) {
    Slot held = slot.get();
    return held != null && held.heldUntilNanos - now > 0;
  }

  private boolean isOnCooldown(NPC npc, long now) {
    Long lastSpokenAt = spokenAtByNpcIndex.get(npc.getIndex());
    return lastSpokenAt != null && now - lastSpokenAt < NPC_COOLDOWN_NANOS;
  }

  private boolean isWithinEarshot(NPC npc) {
    Player local = client.getLocalPlayer();
    if (local == null) {
      return false;
    }
    WorldPoint listener = local.getWorldLocation();
    WorldPoint speaker = npc.getWorldLocation();
    return listener != null && speaker != null && listener.distanceTo(speaker) <= EARSHOT_TILES;
  }

  private static final class Slot {
    private final long heldUntilNanos;

    private Slot(long heldUntilNanos) {
      this.heldUntilNanos = heldUntilNanos;
    }
  }
}
