package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.speaker.AttributeSource;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcAttributes;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcDemographicAnalyzer;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcFinder;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import org.junit.Test;

/** One world scan and one table lookup per line, shared by the voice and the profile. */
public class NpcIdentityResolverTest {

  /** The bundled profile table, loaded once: its child keyword category is the seam under test. */
  private static final NpcProfileTable PROFILE_TABLE = loadProfileTable();

  private final NpcFinder finder = mock(NpcFinder.class);
  private final NpcDemographicAnalyzer analyzer = mock(NpcDemographicAnalyzer.class);
  private final NpcIdentityResolver resolver =
      new NpcIdentityResolver(finder, analyzer, PROFILE_TABLE);

  private static NpcProfileTable loadProfileTable() {
    NpcProfileTable table = new NpcProfileTable();
    table.initialize();
    return table;
  }

  @Test
  public void carriesTheWorldIdAttributesAndNameMatch() {
    NPC npc = mock(NPC.class);
    when(npc.getId()).thenReturn(3501);
    when(finder.findByName("Shilop child")).thenReturn(npc);
    NpcAttributes attributes = new NpcAttributes("Human", "Male", AttributeSource.STATIC_TABLE);
    when(analyzer.analyzeNPC(npc)).thenReturn(attributes);

    NpcIdentity identity = resolver.resolve("Shilop child");

    assertEquals(Integer.valueOf(3501), identity.worldId());
    assertSame(attributes, identity.attributes());
    assertTrue("the child keyword category matched", identity.nameMatch().child());
  }

  @Test
  public void anNpcNotInTheWorldStillCarriesItsNameMatch() {
    when(finder.findByName("Child")).thenReturn(null);

    NpcIdentity identity = resolver.resolve("Child");

    assertNull(identity.worldId());
    assertNull(identity.attributes());
    assertTrue("a name-only child is still a child", identity.nameMatch().child());
  }

  @Test
  public void anUnanalysableNpcKeepsItsWorldId() {
    NPC npc = mock(NPC.class);
    when(npc.getId()).thenReturn(7);
    when(finder.findByName("Hans")).thenReturn(npc);
    when(analyzer.analyzeNPC(npc)).thenReturn(null);

    NpcIdentity identity = resolver.resolve("Hans");

    assertEquals(Integer.valueOf(7), identity.worldId());
    assertNull(identity.attributes());
    assertFalse(identity.nameMatch().child());
  }

  @Test
  public void oneResolutionCostsOneWorldScanAndOneAnalysis() {
    NPC npc = mock(NPC.class);
    when(finder.findByName("Hans")).thenReturn(npc);

    resolver.resolve("Hans");

    verify(finder, times(1)).findByName("Hans");
    verify(analyzer, times(1)).analyzeNPC(npc);
  }

  @Test
  public void aBlankNameNeverScansTheWorldForAMatch() {
    Client client = mock(Client.class);
    NpcIdentityResolver realFinder =
        new NpcIdentityResolver(new NpcFinder(client), analyzer, PROFILE_TABLE);

    NpcIdentity identity = realFinder.resolve("");

    assertNull(identity.worldId());
    assertFalse(identity.nameMatch().child());
  }
}
