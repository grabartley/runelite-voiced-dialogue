package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import com.grahambartley.runelite.voiced.dialogue.speaker.AttributeSource;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcAttributes;
import org.junit.Test;

public class NpcIdentityTest {

  private final NpcProfileTable.NameMatch nameMatch = mock(NpcProfileTable.NameMatch.class);

  @Test
  public void theProfileIdIsTheIdTheAttributesWereFoundUnder() {
    NpcAttributes attributes = new NpcAttributes("Human", "Male", AttributeSource.STATIC_TABLE);
    attributes.setNpcId(8000);
    assertEquals(
        Integer.valueOf(8000), new NpcIdentity(8001, 8000, attributes, nameMatch).profileId());
  }

  @Test
  public void withoutAttributesTheProfileIdIsTheWorldId() {
    assertEquals(Integer.valueOf(42), new NpcIdentity(42, 40, null, nameMatch).profileId());
  }

  @Test
  public void anNpcNotInTheWorldHasNoProfileId() {
    assertNull(new NpcIdentity(null, null, null, nameMatch).profileId());
  }
}
