package com.grahambartley.runelite.voiced.dialogue.panel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

public class NpcListingTest {

  @Test
  public void aListingWithMatchesIsASearch() {
    assertTrue(
        new NpcListing(null, null, Collections.emptyList(), false, Collections.emptySet())
            .searching());
  }

  @Test
  public void aListingWithSectionsIsNotASearch() {
    assertFalse(
        new NpcListing(
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                false,
                Collections.emptySet())
            .searching());
  }
}
