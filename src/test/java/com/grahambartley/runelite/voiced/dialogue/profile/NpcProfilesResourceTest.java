package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class NpcProfilesResourceTest {

  private NpcProfileTable table;

  private NpcProfileTable.Resolution resolve(
      Integer npcId, String npcName, String race, String ethnicity) {
    return table.resolveNpc(npcId, table.matchName(npcName), race, ethnicity);
  }

  private boolean isChild(String npcName) {
    return table.matchName(npcName).child();
  }

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
        new String[] {
          "Human",
          "Elf",
          "Dwarf",
          "Goblin",
          "Monkey",
          "Gorilla",
          "Troll",
          "Undead",
          "Demon",
          "Wizard",
          "Tortugan",
          "Icyene",
          "Arceuus",
          "Aranei",
          "Dog",
          "Crab",
          "Penguin"
        }) {
      assertEquals(
          "race " + race + " resolves to its own bucket",
          "race:" + race,
          resolve(null, "someone", race, null).source());
    }
  }

  @Test
  public void everythingDefaultsToABritishAccent() {
    CharacterProfile p = resolve(null, "A Nameless Stranger", null, null).profile();
    assertTrue("the default accent is British", p.accent().contains("British"));
  }

  @Test
  public void statedSpecialAccentsHold() {
    assertTrue(
        "trolls sound South London / Brixton",
        resolve(null, "Mountain Troll", "Troll", null).profile().accent().contains("Brixton"));
    assertTrue(
        "dwarves sound Scottish",
        resolve(null, "Dwarf Miner", "Dwarf", null).profile().accent().contains("Scottish"));
    assertTrue(
        "gnomes sound country Irish",
        resolve(null, "Gnome Child", "Gnome", null).profile().accent().contains("Irish"));
    assertTrue(
        "leprechauns sound Irish",
        resolve(null, "Tool Leprechaun", "Human", null).profile().accent().contains("Irish"));
    assertTrue(
        "vampyres sound Transylvanian / Dracula-esque",
        resolve(null, "Feral Vampyre", "Undead", null)
            .profile()
            .accent()
            .contains("Transylvanian"));
    assertTrue(
        "gorillas sound deep and booming, not chattery island monkey",
        resolve(null, "Gorilla", "Gorilla", null).profile().accent().contains("booming"));
    assertTrue(
        "tortugans sound Bajan / Barbados",
        resolve(null, "Elder Korel", "Tortugan", null).profile().accent().contains("Barbados"));
    assertTrue(
        "Citizens of Arceuus sound refined and faintly echoing",
        resolve(null, "Tyss", "Arceuus", null).profile().accent().contains("beyond the room"));
    assertTrue(
        "the aranei sound soft-spoken and breathy",
        resolve(null, "Aranei scout", "Aranei", null).profile().accent().contains("breathy"));
    assertTrue(
        "dogs vocalise their lines rather than pronouncing them",
        resolve(null, "Stray dog", "Dog", null).profile().accent().contains("barked"));
    assertTrue(
        "crabs sound bright and West Country seaside",
        resolve(null, "Crab", "Crab", null).profile().accent().contains("West Country"));
    assertTrue(
        "penguins sound Russian",
        resolve(null, "KGP Agent", "Penguin", null).profile().accent().contains("Russian"));
  }

  @Test
  public void theCrabQuestLeadsLayerABespokeStyleOverTheirRaceAccent() {
    NpcProfileTable.Resolution hero = resolve(16469, "Crab", "Crab", null);
    assertTrue("the protagonist resolves his own entry", hero.source().contains("id:16469"));
    assertTrue("he is the bass player", hero.profile().style().contains("bass guitar"));
    assertTrue(
        "the crab accent still carries him", hero.profile().accent().contains("West Country"));

    NpcProfileTable.Resolution lover = resolve(16484, "Crab", "Crab", null);
    assertTrue("the lover resolves her own entry", lover.source().contains("id:16484"));
    assertNotEquals(
        "the lover is not delivered as the protagonist",
        hero.profile().style(),
        lover.profile().style());

    NpcProfileTable.Resolution spy = resolve(16485, "'Crab'", "Penguin", null);
    assertTrue(
        "the penguin in the crab costume resolves his own entry",
        spy.source().contains("id:16485"));
    assertTrue("he is running a disguise", spy.profile().style().contains("costume"));
    assertTrue("the penguin accent carries him", spy.profile().accent().contains("Russian"));
  }

  @Test
  public void penguinsKeepTheirOwnAccentRatherThanTheRegionTheyAreFoundIn() {
    CharacterProfile p = resolve(null, "Pescaling Pax", "Penguin", "fremennik").profile();
    assertTrue("the penguin accent holds over the region", p.accent().contains("Russian"));
    assertFalse("the Fremennik accent does not apply", p.accent().contains("Norse"));
  }

  @Test
  public void dogsKeepTheirVocalisedDeliveryWhereverTheyAreFound() {
    CharacterProfile p = resolve(null, "Stray dog", "Dog", "morytania").profile();
    assertTrue("the dog delivery holds over the region", p.accent().contains("barked"));
    assertFalse("the Morytanian accent does not apply", p.accent().contains("gothic"));
  }

  @Test
  public void arceuusCitizensKeepTheirOwnAccentRatherThanTheKourendOne() {
    CharacterProfile p = resolve(null, "Regath", "Arceuus", "kourend").profile();
    assertTrue("the Arceuus accent holds over the region", p.accent().contains("beyond the room"));
    assertFalse("the rustic Kourend accent does not apply", p.accent().contains("rustic"));
  }

  @Test
  public void aBespokeArceuusStyleLayersOverTheRaceAccent() {
    NpcProfileTable.Resolution r = resolve(7044, "Logosia", "Arceuus", "kourend");
    assertTrue("the bespoke layer contributes", r.source().contains("id:7044"));
    assertTrue("the race layer contributes", r.source().contains("race:Arceuus"));
    assertTrue("her librarian persona survives", r.profile().style().contains("chief librarian"));
    assertTrue(
        "she still speaks with the Arceuus accent",
        r.profile().accent().contains("beyond the room"));
  }

  @Test
  public void ethnicityAccentsHoldFromTheBundledTable() {
    assertTrue(
        "Kharidian desert locals sound Middle Eastern",
        resolve(null, "Desert Trader", "Human", "kharidian")
            .profile()
            .accent()
            .contains("Middle Eastern"));
    assertTrue(
        "Menaphite locals sound Egyptian",
        resolve(null, "Citizen", "Human", "menaphite").profile().accent().contains("Egyptian"));
    assertTrue(
        "Karamja locals sound West African",
        resolve(null, "Trader", "Human", "karamja").profile().accent().contains("African"));
    assertTrue(
        "Fremennik locals sound Norse",
        resolve(null, "Villager", "Human", "fremennik").profile().accent().contains("Norse"));
    assertTrue(
        "Wyrmscraig islanders sound country Irish",
        resolve(null, "Villager", "Human", "wyrmscraig").profile().accent().contains("Irish"));
  }

  @Test
  public void aBespokePerNpcProfileResolvesByIdFromTheBundledTable() {
    NpcProfileTable.Resolution r = resolve(3105, "Hans", "Human", null);
    assertTrue("the bespoke id contributes to the blend", r.source().contains("id:3105"));
    assertEquals("the bespoke name wins", "Hans", r.profile().name());
  }

  @Test
  public void newlyAddedBespokeNpcsResolveByIdFromTheBundledTable() {
    NpcProfileTable.Resolution roald = resolve(1399, "King Roald", "Human", "misthalin");
    assertTrue("King Roald resolves by his bespoke id", roald.source().contains("id:1399"));
    assertEquals("King Roald's bespoke name wins", "King Roald", roald.profile().name());

    NpcProfileTable.Resolution aubury = resolve(10681, "Aubury", "Human", "misthalin");
    assertEquals("Aubury's bespoke name wins", "Aubury", aubury.profile().name());
  }

  @Test
  public void barrowsBrothersResolveGhostlyUndeadWithBespokeId() {
    NpcProfileTable.Resolution ahrim = resolve(1672, "Ahrim the Blighted", "Undead", "misthalin");
    assertTrue("Ahrim keeps his bespoke id layer", ahrim.source().contains("id:1672"));
    assertEquals("Ahrim's bespoke name wins", "Ahrim the Blighted", ahrim.profile().name());
    assertTrue(
        "the Undead racial accent drives the ghostly timbre, not the British default",
        ahrim.source().contains("race:Undead"));
  }

  @Test
  public void bothMortimerFormsShareOneBespokeUndeadProfile() {
    NpcProfileTable.Resolution first = resolve(16175, "Mortimer", "Undead", null);
    NpcProfileTable.Resolution second = resolve(16294, "Mortimer", "Undead", null);
    assertTrue("the bespoke layer contributes", first.source().contains("id:16175"));
    assertTrue("the bespoke layer contributes", second.source().contains("id:16294"));
    assertTrue("the Undead race layer contributes", first.source().contains("race:Undead"));
    assertTrue("the Undead race layer contributes", second.source().contains("race:Undead"));
    assertTrue(
        "his bespoke Irish accent survives the Undead race layer",
        first.profile().accent().contains("Irish"));
    assertTrue("his skeletal persona reads undead", first.profile().style().contains("skeletal"));
    assertTrue(
        "he keeps a brisk Slayer-master delivery over the drawling Undead pace",
        first.profile().pace().contains("Brisk"));
    assertEquals(
        "both forms resolve the same profile directive",
        first.profile().cacheKey(),
        second.profile().cacheKey());
  }

  @Test
  public void thePlayerProfileResolvesFromTheBundledTable() {
    CharacterProfile p = table.resolvePlayer(null, null, null);
    assertTrue("the player has a name label", p.name() != null && !p.name().isEmpty());
    assertTrue("the player accent is British by default", p.accent().contains("British"));
  }

  @Test
  public void theNarratorProfileResolvesFromTheBundledTable() {
    CharacterProfile narrator = table.resolveNarrator();
    assertTrue(
        "the narrator has a name label", narrator.name() != null && !narrator.name().isEmpty());
    assertTrue("the narrator accent is British by default", narrator.accent().contains("British"));
    assertNotEquals(
        "the narrator is not just the player wearing a different label",
        table.resolvePlayer(null, null, null).cacheKey(),
        narrator.cacheKey());
  }

  @Test
  public void childrenKeepTheirRaceOrEthnicityAccentAcrossRaces() {
    NpcProfileTable.Resolution gnome = resolve(6077, "Gnome child", "Gnome", null);
    assertTrue("the child category matched", gnome.source().contains("keyword:child"));
    assertTrue(
        "a gnome child keeps the Irish gnome accent", gnome.profile().accent().contains("Irish"));
    assertTrue(
        "the child delivery layers into the style",
        gnome.profile().style().contains("A young child's voice"));

    NpcProfileTable.Resolution troll = resolve(696, "Troll child", "Troll", null);
    assertTrue(
        "a troll child keeps the troll accent", troll.profile().accent().contains("Brixton"));

    NpcProfileTable.Resolution menaphite = resolve(null, "Child", "Human", "menaphite");
    assertTrue(
        "a Menaphite child keeps the Egyptian accent",
        menaphite.profile().accent().contains("Egyptian"));
  }

  @Test
  public void childNamedNpcsAreMarkedAsChildrenByTheBundledCategory() {
    assertTrue("'Child' is a child", isChild("Child"));
    assertTrue("'Schoolboy' is a child", isChild("Schoolboy"));
    assertTrue("'Schoolgirl' is a child", isChild("Schoolgirl"));
    assertTrue("'Troll child' is a child", isChild("Troll child"));
    assertTrue("'Street urchin' is a child", isChild("Street urchin"));
    assertFalse("'Hans' is not a child", isChild("Hans"));
    assertFalse("'Lady Trahaearn' is not a child", isChild("Lady Trahaearn"));
  }
}
