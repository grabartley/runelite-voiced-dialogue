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
    assertAttributes(385, "Human", "Male");
    assertAttributes(12, "Goblin", "Male");
    assertAttributes(14, "Gnome", "Male");
    assertAttributes(640, "Troll", "Male");
    assertAttributes(1477, "Elf", "Male");
    assertAttributes(229, "Demon", "Male");
    assertAttributes(491, "Undead", "Male");
    assertAttributes(4733, "Dwarf", "Male");
    assertAttributes(7746, "Wizard", "Male");
  }

  @Test
  public void dialogueNpcsResolveToCorrectGenderAndRace() {
    assertAttributes(3105, "Human", "Male");
    assertAttributes(306, "Human", "Male");
    assertAttributes(225, "Human", "Female");
    assertAttributes(2812, "Human", "Male");
    assertAttributes(5037, "Human", "Male");
    assertAttributes(5035, "Human", "Female");
    assertAttributes(4284, "Human", "Female");
    assertAttributes(3561, "Human", "Female");
    assertAttributes(1305, "Human", "Female");
    assertAttributes(11868, "Human", "Female");
    assertAttributes(3481, "Undead", "Male");
    assertAttributes(3893, "Dwarf", "Male");
    assertAttributes(766, "Human", "Male");
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
    for (int npcId : new int[] {104, 105, 135, 964, 1224, 3099, 3133, 7256, 7877, 12374}) {
      assertAttributes(npcId, "Demon", "Male");
    }
  }

  @Test
  public void hellhoundsDraggedBackFromTheGraveAreUndeadFirst() {
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
    for (int npcId :
        new int[] {
          106, 107, 108, 109, 110, 115, 116, 117, 231, 232, 645, 646, 647, 710, 711, 712, 713, 714,
          715, 2490, 2491, 3426, 3912, 4649, 4650, 4651, 9031, 9045, 9181, 10522, 10533, 13812,
          13813
        }) {
      assertAttributes(npcId, "Dog", "Male");
    }
  }

  @Test
  public void theDogShelterHumanAndGoblinCastResolvesToItsDeliberateRaces() {
    assertAttributes(16486, "Human", "Female");
    assertAttributes(16487, "Human", "Male");
    for (int npcId : new int[] {16493, 16539}) {
      assertAttributes(npcId, "Human", "Female");
    }
    for (int npcId : new int[] {16523, 16534, 16524}) {
      assertAttributes(npcId, "Goblin", "Male");
    }
    for (int npcId : new int[] {16527, 16528}) {
      assertAttributes(npcId, "Human", "Male");
    }
  }

  @Test
  public void theDogShelterHumansCarryTheirMisthalinOrigin() {
    for (int npcId : new int[] {16486, 16487, 16493, 16539}) {
      assertEquals("origin for id " + npcId, "misthalin", analyze(npcId, null).getEthnicity());
    }
  }

  @Test
  public void varrockTownsfolkAreNotDraggedIntoTheDogBucket() {
    assertAttributes(7284, "Human", "Female");
    assertEquals("Gertrude stays Misthalin", "misthalin", analyze(7284, null).getEthnicity());
  }

  @Test
  public void reusedIdsTheNameDumpStillRemembersAsDogsResolveToWhatTheyAreNow() {
    for (int npcId : new int[] {14154, 14156}) {
      assertAttributes(npcId, "Human", "Male");
    }
    assertAttributes(14158, "Undead", "Male");
    for (int npcId : new int[] {14162, 14164, 14166}) {
      assertAttributes(npcId, "Demon", "Male");
    }
    assertAttributes(14163, "Dog", "Female");
    for (int npcId : new int[] {14165, 14167, 14169}) {
      assertAttributes(npcId, "Dog", "Male");
    }
  }

  @Test
  public void theCrabQuestCastResolvesAsCrabsOfItsStatedGender() {
    for (int npcId :
        new int[] {16469, 16470, 16471, 16472, 16473, 16474, 16479, 16480, 16481, 16482, 16483}) {
      assertAttributes(npcId, "Crab", "Male");
    }
    assertAttributes(16484, "Crab", "Female");
  }

  @Test
  public void theCrabCostumeOnDognoseIslandIsAPenguinUnderneath() {
    assertAttributes(16485, "Penguin", "Male");
  }

  @Test
  public void theQuestsFishingSpotStaysOffTheCrabRace() {
    for (int npcId : new int[] {16475, 16476, 16477, 16478}) {
      assertAttributes(npcId, "Human", "Male");
    }
  }

  @Test
  public void thePenguinsOfGielinorResolveAsPenguinsRatherThanAsTownsfolk() {
    for (int npcId :
        new int[] {
          233, 731, 830, 831, 832, 833, 834, 835, 836, 837, 838, 839, 840, 841, 842, 844, 845, 847,
          848, 849, 850, 851, 2063
        }) {
      assertAttributes(npcId, "Penguin", "Male");
    }
  }

  @Test
  public void theCrabsAlreadyScatteredAroundGielinorPickUpTheCrabRace() {
    for (int npcId :
        new int[] {1040, 1553, 7576, 7577, 7578, 7579, 7799, 7800, 8733, 9201, 10563, 14939}) {
      assertAttributes(npcId, "Crab", "Male");
    }
  }

  @Test
  public void anIdTwoWikiPagesBothClaimResolvesToTheOneTheCacheAgreesWith() {
    assertAttributes(13247, "Human", "Female");
  }

  @Test
  public void femaleNamedTownsfolkResolveFemale() {
    assertAttributes(7284, "Human", "Female");
    assertAttributes(3214, "Human", "Female");
  }

  @Test
  public void knownEntriesAreMarkedAsTableSourced() {
    NpcAttributes hans = analyze(3105, "Hans");
    assertEquals(AttributeSource.STATIC_TABLE, hans.getSource());
    assertEquals(3105, hans.getNpcId());
  }

  @Test
  public void aTransformedNpcFallsBackToItsBaseId() {
    NpcAttributes attributes = analyze(999_000_001, 3105, "Hans");
    assertEquals(AttributeSource.STATIC_TABLE, attributes.getSource());
    assertEquals(3105, attributes.getNpcId());
  }

  @Test
  public void unknownIdFallsBackToUnknownRaceSoFallbackVoiceApplies() {
    NpcAttributes attributes = analyze(987654321, "Totally Made Up NPC");
    assertNotNull(attributes);
    assertEquals("Unknown", attributes.getRace());
    assertEquals("Male", attributes.getGender());
    assertEquals(AttributeSource.DEFAULT, attributes.getSource());
    assertEquals(987654321, attributes.getNpcId());
  }

  @Test
  public void unknownFemaleNamedNpcGetsBestGuessFemaleGender() {
    assertEquals("Female", analyze(987654322, "Mysterious Woman").getGender());
    assertEquals("Female", analyze(987654323, "Lost Princess").getGender());
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
    analyzer = new NpcDemographicAnalyzer();
    NpcAttributes attributes = analyze(101, "Goblin");
    assertEquals("Unknown", attributes.getRace());
    assertEquals("Male", attributes.getGender());
    assertEquals(AttributeSource.DEFAULT, attributes.getSource());
  }

  @Test
  public void markedChildrenCarryTheChildLifeStageFromTheBundledTable() {
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
