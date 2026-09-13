package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import com.grahambartley.runelite.voiced.dialogue.speaker.LearnedNpcStore;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcAttributes;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class NpcLearningService {

  private static final String DIALOGUE_OPTION_PREFIX = "talk";

  private static final int FAILURES_BEFORE_QUIET = 3;

  private static final long QUIET_MILLIS = TimeUnit.MINUTES.toMillis(2);

  private final WikiNpcClient client;
  private final LearnedNpcStore store;
  private final Executor executor;
  private final BooleanSupplier enabled;
  private final IntPredicate voiced;
  private final WikiCallThrottle throttle;
  private final Set<Integer> attempted = ConcurrentHashMap.newKeySet();

  private final AtomicInteger consecutiveFailures = new AtomicInteger();

  private volatile long quietUntilMillis;

  private volatile boolean closed;

  public NpcLearningService(
      WikiNpcClient client,
      LearnedNpcStore store,
      Executor executor,
      BooleanSupplier enabled,
      IntPredicate voiced,
      WikiCallThrottle throttle) {
    this.client = client;
    this.store = store;
    this.executor = executor;
    this.enabled = enabled;
    this.voiced = voiced;
    this.throttle = throttle;
  }

  public void close() {
    closed = true;
  }

  public boolean isEnabled() {
    return enabled.getAsBoolean();
  }

  public boolean startsConversation(String menuOption) {
    return menuOption != null
        && menuOption.regionMatches(
            true, 0, DIALOGUE_OPTION_PREFIX, 0, DIALOGUE_OPTION_PREFIX.length());
  }

  public void considerLearning(int npcId, String npcName) {
    if (closed || !enabled.getAsBoolean() || npcName == null || npcName.isEmpty()) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now < quietUntilMillis) {
      return;
    }
    if (voiced.test(npcId) || !store.isPastMissWindow(npcId, now) || !attempted.add(npcId)) {
      return;
    }
    executor.execute(
        () -> {
          if (closed) {
            attempted.remove(npcId);
            return;
          }
          throttle.awaitTurn();
          if (closed) {
            attempted.remove(npcId);
            return;
          }
          WikiLookup lookup = client.lookup(npcId, npcName);
          if (lookup.isUnreachable()) {
            attempted.remove(npcId);
            if (consecutiveFailures.incrementAndGet() >= FAILURES_BEFORE_QUIET) {
              quietUntilMillis = System.currentTimeMillis() + QUIET_MILLIS;
            }
            log.debug("[TTS learn] wiki was unreachable for '{}' (id {})", npcName, npcId);
            return;
          }
          consecutiveFailures.set(0);
          if (lookup.isUndocumented()) {
            store.missed(npcId, System.currentTimeMillis());
            log.debug("[TTS learn] wiki had no usable entry for '{}' (id {})", npcName, npcId);
            return;
          }
          NpcAttributes attributes = lookup.attributes();
          store.learn(
              npcId, attributes.getRace(), attributes.getGender(), attributes.getEthnicity());
          log.info(
              "[TTS learn] learned '{}' (id {}) from wiki -> race={} gender={} ethnicity={}",
              npcName,
              npcId,
              attributes.getRace(),
              attributes.getGender(),
              attributes.getEthnicity() == null ? "-" : attributes.getEthnicity());
        });
  }
}
