package com.grahambartley.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.synthesis.CharacterProfile;
import org.junit.Before;
import org.junit.Test;

/**
 * Validates the character profiles actually bundled in {@code /npc-voices.json}: the section loads,
 * every race bucket resolves, the stated special accents hold, and the player and a bespoke NPC
 * resolve. Guards against a malformed or regenerated resource shipping broken profiles.
 */
public class NpcProfilesResourceTest {

  private NpcProfileTable table;

  @Before
  public void setUp() {
    table = new NpcProfileTable();
    table.initialize();
  }

  @Test
  public void theBundledProfilesSectionLoads() {
    assertTrue("the bundled profiles section loaded", table.isLoaded());
  }

  @Test
  public void everyRaceBucketResolvesToItsOwnLayer() {
    for (String race :
        new String[] {"Human", "Elf", "Dwarf", "Goblin", "Troll", "Undead", "Demon", "Wizard"}) {
      assertEquals(
          "race " + race + " resolves to its own bucket",
          "race:" + race,
          table.resolveNpc(null, "someone", race).source());
    }
  }

  @Test
  public void everythingDefaultsToABritishAccent() {
    // An NPC with no race and no keyword match must still get a British default.
    CharacterProfile p = table.resolveNpc(null, "A Nameless Stranger", null).profile();
    assertTrue("the default accent is British", p.accent().contains("British"));
  }

  @Test
  public void statedSpecialAccentsHold() {
    assertTrue(
        "trolls sound South London / Brixton",
        table.resolveNpc(null, "Mountain Troll", "Troll").profile().accent().contains("Brixton"));
    assertTrue(
        "dwarves sound Scottish",
        table.resolveNpc(null, "Dwarf Miner", "Dwarf").profile().accent().contains("Scottish"));
    assertTrue(
        "leprechauns sound Irish",
        table.resolveNpc(null, "Tool Leprechaun", "Human").profile().accent().contains("Irish"));
    assertTrue(
        "vampyres sound Transylvanian / Dracula-esque",
        table
            .resolveNpc(null, "Feral Vampyre", "Undead")
            .profile()
            .accent()
            .contains("Transylvanian"));
    assertTrue(
        "Fremennik sound Scandinavian",
        table
            .resolveNpc(null, "Thorvald the Warrior", "Human")
            .profile()
            .accent()
            .contains("Scandinavian"));
  }

  @Test
  public void aBespokePerNpcProfileResolvesByIdFromTheBundledTable() {
    // Hans (id 3105) is in the iconic batch.
    NpcProfileTable.Resolution r = table.resolveNpc(3105, "Hans", "Human");
    assertEquals("id:3105", r.source());
    assertEquals("Hans", r.profile().name());
  }

  @Test
  public void thePlayerProfileResolvesFromTheBundledTable() {
    CharacterProfile p = table.resolvePlayer(null, null, null);
    assertTrue("the player has a name label", p.name() != null && !p.name().isEmpty());
    assertTrue("the player accent is British by default", p.accent().contains("British"));
  }
}
