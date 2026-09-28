package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class NpcVoiceImportTest {

  private static final NpcVoiceOverride WARM = new NpcVoiceOverride(null, null, "Warm", null, null);

  @Test
  public void holdsACopySortedById() {
    Map<Integer, NpcVoiceOverride> source = new HashMap<>();
    source.put(9, WARM);
    source.put(2, WARM);

    NpcVoiceImport imported = new NpcVoiceImport(source, 3);
    source.put(5, WARM);

    assertEquals(Arrays.asList(2, 9), Arrays.asList(imported.overrides().keySet().toArray()));
    assertEquals(3, imported.skipped());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void theOverridesCannotBeChanged() {
    new NpcVoiceImport(new HashMap<>(), 0).overrides().put(1, WARM);
  }
}
