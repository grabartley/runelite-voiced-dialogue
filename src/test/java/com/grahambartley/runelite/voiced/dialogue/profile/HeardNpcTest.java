package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class HeardNpcTest {

  @Test
  public void carriesTheIdAndName() {
    HeardNpc heard = new HeardNpc(3105, "Hans");

    assertEquals(3105, heard.id());
    assertEquals("Hans", heard.name());
  }

  @Test
  public void equalityIsByIdAndName() {
    assertEquals(new HeardNpc(1, "Hans"), new HeardNpc(1, "Hans"));
    assertNotEquals(new HeardNpc(1, "Hans"), new HeardNpc(2, "Hans"));
    assertNotEquals(new HeardNpc(1, "Hans"), new HeardNpc(1, "Bob"));
  }
}
