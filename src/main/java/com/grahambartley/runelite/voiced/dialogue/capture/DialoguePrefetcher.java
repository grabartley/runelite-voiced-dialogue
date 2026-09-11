package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class DialoguePrefetcher {

  static final int MAX_PER_SESSION = 10;

  private final Consumer<SynthesisRequest> sink;
  private final Runnable canceller;

  private final Set<SynthesisRequest> submitted = new HashSet<>();

  private int count;

  public DialoguePrefetcher(Consumer<SynthesisRequest> sink, Runnable canceller) {
    this.sink = sink;
    this.canceller = canceller;
  }

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

  public void reset() {
    submitted.clear();
    count = 0;
    canceller.run();
  }
}
