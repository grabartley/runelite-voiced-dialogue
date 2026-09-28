package com.grahambartley.runelite.voiced.dialogue.panel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class NpcListEntryTest {

  @Test
  public void carriesEveryField() {
    NpcListEntry entry = new NpcListEntry("Guard", Arrays.asList(1, 2), true, false, 2);

    assertEquals("Guard", entry.name());
    assertEquals(Arrays.asList(1, 2), entry.ids());
    assertTrue(entry.heard());
    assertFalse(entry.edited());
    assertEquals(2, entry.preferredId());
  }
}
