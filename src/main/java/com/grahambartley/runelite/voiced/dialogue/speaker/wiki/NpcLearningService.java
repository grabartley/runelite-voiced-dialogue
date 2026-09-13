package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import com.grahambartley.runelite.voiced.dialogue.speaker.LearnedNpcStore;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcAttributes;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class NpcLearningService {

  private static final String DIALOGUE_OPTION_PREFIX = "talk";

  private final WikiNpcClient client;
  private final LearnedNpcStore store;
  private final Executor executor;
  private final BooleanSupplier enabled;
  private final WikiCallThrottle throttle;
  private final Set<Integer> attempted = ConcurrentHashMap.newKeySet();

  public NpcLearningService(
      WikiNpcClient client,
      LearnedNpcStore store,
      Executor executor,
      BooleanSupplier enabled,
      WikiCallThrottle throttle) {
    this.client = client;
    this.store = store;
    this.executor = executor;
    this.enabled = enabled;
    this.throttle = throttle;
  }

  public static boolean isDialogueOption(String menuOption) {
    return menuOption != null
        && menuOption.trim().toLowerCase(Locale.ROOT).startsWith(DIALOGUE_OPTION_PREFIX);
  }

  public void considerLearning(int npcId, String npcName) {
    if (!enabled.getAsBoolean() || npcName == null || npcName.isEmpty()) {
      return;
    }
    if (!store.isWorthLooking(npcId, System.currentTimeMillis()) || !attempted.add(npcId)) {
      return;
    }
    executor.execute(
        () -> {
          if (!throttle.awaitTurn()) {
            attempted.remove(npcId);
            return;
          }
          WikiLookup lookup = client.lookup(npcId, npcName);
          if (lookup.isUnreachable()) {
            attempted.remove(npcId);
            log.debug("[TTS learn] wiki was unreachable for '{}' (id {})", npcName, npcId);
            return;
          }
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
