package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;

public class NpcVoiceImportPlanTest {

  private static final NpcVoiceOverride OLD = new NpcVoiceOverride(null, null, "Old", null, null);
  private static final NpcVoiceOverride NEW = new NpcVoiceOverride(null, null, "New", null, null);

  private final NpcVoiceOverrideStore store = new NpcVoiceOverrideStore(mock(ConfigManager.class));

  private NpcVoiceImportPlan plan(int skipped, Integer... importedIds) {
    Map<Integer, NpcVoiceOverride> overrides = new HashMap<>();
    for (Integer id : importedIds) {
      overrides.put(id, NEW);
    }
    return new NpcVoiceImportPlan(new NpcVoiceImport(overrides, skipped), store.overriddenIds());
  }

  private void existing(Integer... ids) {
    for (Integer id : ids) {
      store.set(id, OLD);
    }
  }

  @Test
  public void countsWhatTheImportSetsReplacesAndSkipped() {
    existing(1, 2, 3);

    NpcVoiceImportPlan plan = plan(4, 2, 3, 7);

    assertEquals(3, plan.sets());
    assertEquals(2, plan.replaces());
    assertEquals(4, plan.skipped());
    assertEquals(1, plan.clearedByReplaceAll());
  }

  @Test
  public void anImportOntoNoEditsReplacesAndClearsNothing() {
    NpcVoiceImportPlan plan = plan(0, 5, 6);

    assertEquals(2, plan.sets());
    assertEquals(0, plan.replaces());
    assertEquals(0, plan.clearedByReplaceAll());
  }

  @Test
  public void mergeKeepsEditsForIdsNotInTheFile() {
    existing(1, 2);

    plan(0, 2, 3).apply(store, NpcVoiceImportPlan.Mode.MERGE);

    assertEquals(new HashSet<>(Arrays.asList(1, 2, 3)), store.overriddenIds());
    assertEquals(OLD, store.get(1));
    assertEquals(NEW, store.get(2));
    assertEquals(NEW, store.get(3));
  }

  @Test
  public void replaceAllLeavesOnlyTheImportedIds() {
    existing(1, 2);

    plan(0, 2, 3).apply(store, NpcVoiceImportPlan.Mode.REPLACE_ALL);

    assertEquals(new HashSet<>(Arrays.asList(2, 3)), store.overriddenIds());
    assertEquals(NEW, store.get(2));
  }

  @Test
  public void theCountsAreFixedWhenThePlanIsMade() {
    existing(1);
    NpcVoiceImportPlan plan = plan(0, 1);

    existing(9);

    assertEquals(1, plan.replaces());
    assertEquals(0, plan.clearedByReplaceAll());
  }
}
