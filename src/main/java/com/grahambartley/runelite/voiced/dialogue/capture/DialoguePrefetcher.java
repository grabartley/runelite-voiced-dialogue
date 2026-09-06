package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Decides which speculative dialogue lines to warm, between the game thread that reads the dialogue
 * widgets and the {@link com.grahambartley.runelite.voiced.dialogue.speech.DialogueAudioService}
 * that actually synthesizes them.
 *
 * <p>On each tick the plugin offers the lines reachable from the current node (the visible dialogue
 * options the player could pick next); this class forwards the ones not already offered this
 * dialogue session to the warm sink, deduplicated so the same options are not re-submitted every
 * tick, and hard-capped at {@link #MAX_PER_SESSION} so a session can never fan out into unbounded
 * speculative spend. {@link #reset()} clears that state and cancels still-queued prefetches once
 * the dialogue is fully closed, so warming never spills onto a branch the player has left.
 *
 * <p>All calls happen on the game thread, so the dedup state needs no synchronisation; the heavy
 * synthesis happens behind the sink, off-thread.
 */
public final class DialoguePrefetcher {

  /**
   * Hard ceiling on distinct prefetches warmed per dialogue session, bounding speculative spend.
   */
  static final int MAX_PER_SESSION = 10;

  private final Consumer<SynthesisRequest> sink;
  private final Runnable canceller;

  /** Requests already offered this session, so re-reading the same options each tick is a no-op. */
  private final Set<SynthesisRequest> submitted = new HashSet<>();

  private int count;

  /**
   * @param sink receives each newly-offered request to warm, off-thread
   * @param canceller cancels still-queued prefetches on reset
   */
  public DialoguePrefetcher(Consumer<SynthesisRequest> sink, Runnable canceller) {
    this.sink = sink;
    this.canceller = canceller;
  }

  /** Empty/blank candidates and duplicates are skipped without consuming the cap. */
  public void offer(List<SynthesisRequest> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return;
    }
    for (SynthesisRequest request : candidates) {
      if (count >= MAX_PER_SESSION) {
        break;
      }
      if (request == null || request.text() == null || request.text().isEmpty()) {
        continue;
      }
      if (!submitted.add(request)) {
        continue;
      }
      count++;
      sink.accept(request);
    }
  }

  /**
   * Resets per-session state and cancels still-queued prefetches, so the next conversation starts
   * with a fresh cap and no stale speculation.
   */
  public void reset() {
    submitted.clear();
    count = 0;
    canceller.run();
  }
}
