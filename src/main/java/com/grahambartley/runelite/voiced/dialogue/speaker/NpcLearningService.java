package com.grahambartley.runelite.voiced.dialogue.speaker;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class NpcLearningService {

  private final WikiNpcClient client;
  private final LearnedNpcStore store;
  private final Executor executor;
  private final BooleanSupplier enabled;
  private final Set<Integer> attempted = ConcurrentHashMap.newKeySet();

  public NpcLearningService(
      WikiNpcClient client, LearnedNpcStore store, Executor executor, BooleanSupplier enabled) {
    this.client = client;
    this.store = store;
    this.executor = executor;
    this.enabled = enabled;
  }

  public void considerLearning(int npcId, String npcName) {
    if (!enabled.getAsBoolean() || npcName == null || npcName.isEmpty()) {
      return;
    }
    if (store.contains(npcId) || !attempted.add(npcId)) {
      return;
    }
    executor.execute(
        () -> {
          NpcAttributes attributes = client.lookup(npcName);
          if (attributes == null) {
            log.debug("[TTS learn] wiki had no usable entry for '{}' (id {})", npcName, npcId);
            return;
          }
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
