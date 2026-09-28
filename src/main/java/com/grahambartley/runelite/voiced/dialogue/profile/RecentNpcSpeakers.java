package com.grahambartley.runelite.voiced.dialogue.profile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class RecentNpcSpeakers {

  static final int CAPACITY = 30;

  private final Deque<HeardNpc> ring = new ArrayDeque<>();
  private volatile Runnable listener = () -> {};

  public void record(int npcId, String npcName) {
    if (npcName == null || npcName.trim().isEmpty()) {
      return;
    }
    HeardNpc heard = new HeardNpc(npcId, npcName.trim());
    synchronized (this) {
      if (heard.equals(ring.peekFirst())) {
        return;
      }
      ring.remove(heard);
      ring.addFirst(heard);
      while (ring.size() > CAPACITY) {
        ring.removeLast();
      }
    }
    listener.run();
  }

  public synchronized List<HeardNpc> newestFirst() {
    return new ArrayList<>(ring);
  }

  public void setListener(Runnable listener) {
    this.listener = listener == null ? () -> {} : listener;
  }
}
