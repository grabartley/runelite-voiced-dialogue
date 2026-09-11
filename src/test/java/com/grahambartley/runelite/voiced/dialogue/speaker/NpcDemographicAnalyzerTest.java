package com.grahambartley.runelite.voiced.dialogue.speaker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies the static NPC voice table lookup: known ids resolve to the baked-in race/gender,
 * unknown ids fall back deterministically, a transformed NPC falls back to its base id, and no live
 * data source is consulted.
 */
public class NpcDemographicAnalyzerTest {

  private NpcDemographicAnalyzer analyzer;

  @Before
  public void setUp() {
    analyzer = new NpcDemographicAnalyzer();
    analyzer.initialize();
  }

  @Test
  public void bundledTableLoadsEntries() {
    assertTrue("expected the bundled table to load many entries", analyzer.getTableSize() > 500);
  }

  @Test
  public void knownNpcsResolveToCorrectRaceAndGender() {
    // Real OSRS cache ids (the same ids the live client reports), spanning each
    // distinctive race bucket, with race/gender taken straight from the wiki.
    assertAttributes(385, "Human", "Male"); // Man
    assertAttributes(12, "Goblin", "Male"); // Goblin
    assertAttributes(14, "Gnome", "Male"); // Gnome
    assertAttributes(640, "Troll", "Male"); // Troll
    assertAttributes(1477, "Elf", "Male"); // Elf
    assertAttributes(229, "Demon", "Male"); // Demon
    assertAttributes(491, "Undead", "Male"); // Undead
    assertAttributes(4733, "Dwarf", "Male"); // Thurgo
    assertAttributes(7746, "Wizard", "Male"); // Wizard Mizgog
  }

  @Test
  public void dialogueNpcsResolveToCorrectGenderAndRace() {
    // High-traffic peaceful dialogue NPCs, so male and female townsfolk get distinct voices instead
    // of collapsing to the human-male default. Ids are real cache ids verified against the osrs
    // data.
    assertAttributes(3105, "Human", "Male"); // Hans
    assertAttributes(306, "Human", "Male"); // Lumbridge Guide
    assertAttributes(225, "Human", "Female"); // Cook (servant), female per the wiki
    assertAttributes(2812, "Human", "Male"); // Father Aereck
    assertAttributes(5037, "Human", "Male"); // Romeo
    assertAttributes(5035, "Human", "Female"); // Juliet
    assertAttributes(4284, "Human", "Female"); // Aggie
    assertAttributes(3561, "Human", "Female"); // Veronica
    assertAttributes(1305, "Human", "Female"); // Hairdresser
    assertAttributes(11868, "Human", "Female"); // Aris (Gypsy)
    assertAttributes(3481, "Undead", "Male"); // Count Draynor
    assertAttributes(3893, "Dwarf", "Male"); // Doric
    assertAttributes(766, "Human", "Male"); // Banker
  }

  @Test
  public void bothMortimerFormsResolveAsTheSameUndeadSkeleton() {
    assertAttributes(16175, "Undead", "Male");
    assertAttributes(16294, "Undead", "Male");
    assertNull("Mortimer carries no ethnicity tint", analyze(16175, null).getEthnicity());
    assertNull("Mortimer carries no ethnicity tint", analyze(16294, null).getEthnicity());
  }

  @Test
  public void everyAraneiFormResolvesToItsOwnRaceRatherThanHuman() {
    // Sarei is the Mysterious Stranger in every form but the replacement, which is the separate
    // aranei who takes her post at the Theatre of Blood after Drakan kills her.
    for (int npcId :
        new int[] {
          15749, 15750, 15752, 15754, 15737, 15738, 15762, 16269, 16270, 9639, 9640, 16360
        }) {
      assertAttributes(npcId, "Aranei", "Male");
    }
    for (int npcId :
        new int[] {
          16268, 8208, 10875, 10876, 10877, 11162, 16005, 16006, 16007, 16008, 16009, 16242
        }) {
      assertAttributes(npcId, "Aranei", "Female");
    }
  }

  @Test
  public void theBloodMoonCastResolvesToItsDeliberateRaces() {
    assertAttributes(16212, "Undead", "Male");
    for (int npcId : new int[] {11181, 15967, 15968, 15969, 16190}) {
      assertAttributes(npcId, "Demon", "Female");
    }
    for (int npcId : new int[] {15893, 15894, 15895, 15896, 15897, 15898, 15879, 15885}) {
      assertAttributes(npcId, "Human", "Male");
    }
  }

  @Test
  public void theSlavesCarryTheirPerVersionGenders() {
    for (int npcId : new int[] {16163, 16164, 16165}) {
      assertAttributes(npcId, "Human", "Male");
    }
    for (int npcId : new int[] {16166, 16167, 16168}) {
      assertAttributes(npcId, "Human", "Female");
    }
  }

  @Test
  public void theBloodMoonMorytaniansKeepTheirRegionalOrigin() {
    for (int npcId :
        new int[] {15893, 15894, 15895, 15896, 15897, 15898, 15879, 15885, 16163, 16166}) {
      assertEquals("origin for id " + npcId, "morytania", analyze(npcId, null).getEthnicity());
    }
  }

  @Test
  public void theBloodMoonDistinctiveRacesCarryNoRegionalTint() {
    for (int npcId : new int[] {16268, 16005, 8208, 11181, 16212}) {
      assertNull("no origin for id " + npcId, analyze(npcId, null).getEthnicity());
    }
  }

  @Test
  public void theDogShelterCanineCastResolvesAsDogsOfItsStatedGender() {
    for (int npcId : new int[] {16504, 16505, 16529, 16530, 16495, 16515, 16531, 16517, 16533}) {
      assertAttributes(npcId, "Dog", "Female");
    }
    for (int npcId : new int[] {16516, 16532}) {
      assertAttributes(npcId, "Dog", "Male");
    }
  }

  @Test
  public void theAdoptableShelterPuppiesTakeTheirGendersStraightFromTheWiki() {
    // These carry a gender per breed on the page, so they resolve without an override.
    for (int npcId : new int[] {16512, 16518, 16514, 16520}) {
      assertAttributes(npcId, "Dog", "Female");
    }
    for (int npcId : new int[] {16513, 16519}) {
      assertAttributes(npcId, "Dog", "Male");
    }
  }

  @Test
  public void theWanderingBreedDogsResolveAsDogsWithoutAnOverride() {
    for (int npcId :
        new int[] {
          16398, 16416, 16400, 16418, 16402, 16420, 16404, 16422, 16406, 16424, 16408, 16426, 16410,
          16428, 16412, 16430, 16414, 16432
        }) {
      assertAttributes(npcId, "Dog", "Male");
    }
  }

  @Test
  public void dogsTheWikiLeavesUnlabelledStillResolveAsDogs() {
    // Their pages carry an Infobox Monster or omit the race field, so the bucket comes from a pin.
    for (int npcId :
        new int[] {112, 113, 114, 131, 7209, 7771, 12992, 12993, 12994, 12995, 12999}) {
      assertAttributes(npcId, "Dog", "Male");
    }
    for (int npcId : new int[] {2802, 10438, 10439, 12998}) {
      assertAttributes(npcId, "Dog", "Female");
    }
  }

  @Test
  public void theWholeHellhoundFamilyResolvesToOneRaceRatherThanToWhicheverPageClaimedIt() {
    for (int npcId : new int[] {104, 105, 135, 964, 1224, 3099, 3133, 7256, 7877, 12374, 13247}) {
      assertAttributes(npcId, "Demon", "Male");
    }
  }

  @Test
  public void hellhoundsDraggedBackFromTheGraveAreUndeadFirst() {
    // Skeletal, revenant and reanimated all outrank hellhound in the scan, so the grave wins.
    for (int npcId : new int[] {5054, 6326, 6387, 6613, 6614, 7025, 7935, 11463, 12107, 12108}) {
      assertAttributes(npcId, "Undead", "Male");
    }
  }

  @Test
  public void houndsTheWikiCallsDogLikeVoiceAsDogs() {
    for (int npcId : new int[] {3449, 4185, 6473, 6474, 11583}) {
      assertAttributes(npcId, "Dog", "Male");
    }
  }

  @Test
  public void wolvesVoiceAsDogsRatherThanAsPeople() {
    for (int npcId : new int[] {106, 116, 117, 2490, 2491, 3912}) {
      assertAttributes(npcId, "Dog", "Male");
    }
  }

  @Test
  public void theDogShelterHumanAndGoblinCastResolvesToItsDeliberateRaces() {
    assertAttributes(16486, "Human", "Female"); // Talia
    assertAttributes(16487, "Human", "Male"); // Chase
    for (int npcId : new int[] {16493, 16539}) {
      assertAttributes(npcId, "Human", "Female"); // Guard
    }
    for (int npcId : new int[] {16523, 16534, 16524}) {
      assertAttributes(npcId, "Goblin", "Male"); // Picklenose, Toetaller
    }
    for (int npcId : new int[] {16527, 16528}) {
      assertAttributes(npcId, "Human", "Male"); // Outlaw
    }
  }

  @Test
  public void theDogShelterHumansCarryTheirMisthalinOrigin() {
    for (int npcId : new int[] {16486, 16487, 16493, 16539}) {
      assertEquals("origin for id " + npcId, "misthalin", analyze(npcId, null).getEthnicity());
    }
  }

  @Test
  public void theDogShelterRegularsStayHumanMisthalinTownsfolk() {
    assertAttributes(7284, "Human", "Female"); // Gertrude
    assertEquals("Gertrude stays Misthalin", "misthalin", analyze(7284, null).getEthnicity());
    assertAttributes(766, "Human", "Male"); // Banker
  }

  @Test
  public void femaleNamedTownsfolkResolveFemale() {
    // Gender comes straight from the wiki, so townsfolk with no gendered title
    // (Gertrude, Cassie) still resolve Female instead of defaulting to male.
    assertAttributes(7284, "Human", "Female"); // Gertrude
    assertAttributes(3214, "Human", "Female"); // Cassie
  }

  @Test
  public void knownEntriesAreMarkedAsTableSourced() {
    NpcAttributes hans = analyze(3105, "Hans");
    assertEquals(AttributeSource.STATIC_TABLE, hans.getSource());
    assertEquals(3105, hans.getNpcId());
  }

  @Test
  public void aTransformedNpcFallsBackToItsBaseId() {
    // A multiloc NPC reports an active id the table does not know and a base (composition) id it
    // does, so the base id must still find the entry.
    NpcAttributes attributes = analyze(999_000_001, 3105, "Hans");
    assertEquals(AttributeSource.STATIC_TABLE, attributes.getSource());
    assertEquals(3105, attributes.getNpcId());
  }

  @Test
  public void unknownIdFallsBackToUnknownRaceSoFallbackVoiceApplies() {
    // Race must be Unknown (not Human) so voice resolution routes through the configured fallback
    // voice rather than silently using the human voice.
    NpcAttributes attributes = analyze(987654321, "Totally Made Up NPC");
    assertNotNull(attributes);
    assertEquals("Unknown", attributes.getRace());
    assertEquals("Male", attributes.getGender());
    assertEquals(AttributeSource.DEFAULT, attributes.getSource());
    assertEquals(987654321, attributes.getNpcId());
  }

  @Test
  public void unknownFemaleNamedNpcGetsBestGuessFemaleGender() {
    // The lone runtime name check: an explicit female word reports a best-guess Female gender for
    // missing-id NPCs. Race stays Unknown, which voices with the single default voice regardless.
    assertEquals("Female", analyze(987654322, "Mysterious Woman").getGender());
    assertEquals("Female", analyze(987654323, "Lost Princess").getGender());
    // No female signal stays Male; a substring inside a larger word must not trigger it.
    assertEquals("Male", analyze(987654324, "Old Sailor").getGender());
    assertEquals("Male", analyze(987654325, "Womanizer Larry").getGender());
  }

  @Test
  public void unknownIdFallbackIsStable() {
    NpcAttributes first = analyze(424242, "Unknown");
    NpcAttributes second = analyze(424242, "Unknown");
    assertEquals(first.getRace(), second.getRace());
    assertEquals(first.getGender(), second.getGender());
  }

  @Test
  public void nullNpcReturnsNull() {
    assertNull(analyzer.analyzeNPC(null));
  }

  @Test
  public void anNpcWithoutACompositionReturnsNull() {
    NPC npc = mock(NPC.class);
    when(npc.getComposition()).thenReturn(null);
    assertNull(analyzer.analyzeNPC(npc));
  }

  @Test
  public void learnedStoreIsConsultedForIdsMissingFromTheBundledTable() throws Exception {
    Path file = Files.createTempDirectory("learned").resolve("l.json");
    LearnedNpcStore store = new LearnedNpcStore(file, new Gson());
    store.learn(987001, "Elf", "Female", "tirannwn");
    analyzer.setLearnedStore(store);

    NpcAttributes a = analyze(987001, "A New Elf");
    assertEquals("Elf", a.getRace());
    assertEquals("Female", a.getGender());
    assertEquals("tirannwn", a.getEthnicity());
    assertEquals(AttributeSource.LEARNED, a.getSource());
  }

  @Test
  public void lookupWorksWithoutInitializeUsingDefault() {
    // A fresh analyzer that was never initialized must still resolve safely (empty table ->
    // default) rather than throwing, so a missing resource can never break voice selection.
    analyzer = new NpcDemographicAnalyzer();
    NpcAttributes attributes = analyze(101, "Goblin");
    assertEquals("Unknown", attributes.getRace());
    assertEquals("Male", attributes.getGender());
    assertEquals(AttributeSource.DEFAULT, attributes.getSource());
  }

  @Test
  public void markedChildrenCarryTheChildLifeStageFromTheBundledTable() {
    // Real child NPCs marked via overrides.json: Shilop (Gertrude's son) and Rory (young cyclops).
    assertTrue("Shilop is a child", analyze(3501, null).isChild());
    assertTrue("Rory is a child", analyze(2136, null).isChild());
  }

  @Test
  public void unmarkedNpcsAreAdults() {
    assertFalse("Hans is an adult", analyze(3105, null).isChild());
  }

  private void assertAttributes(int npcId, String expectedRace, String expectedGender) {
    NpcAttributes attributes = analyze(npcId, null);
    assertNotNull("expected a table entry for id " + npcId, attributes);
    assertEquals("race for id " + npcId, expectedRace, attributes.getRace());
    assertEquals("gender for id " + npcId, expectedGender, attributes.getGender());
  }

  private NpcAttributes analyze(int npcId, String npcName) {
    return analyze(npcId, npcId, npcName);
  }

  private NpcAttributes analyze(int activeId, int baseId, String npcName) {
    NPCComposition composition = mock(NPCComposition.class);
    when(composition.getId()).thenReturn(baseId);
    when(composition.getName()).thenReturn(npcName);
    NPC npc = mock(NPC.class);
    when(npc.getId()).thenReturn(activeId);
    when(npc.getComposition()).thenReturn(composition);
    return analyzer.analyzeNPC(npc);
  }
}
