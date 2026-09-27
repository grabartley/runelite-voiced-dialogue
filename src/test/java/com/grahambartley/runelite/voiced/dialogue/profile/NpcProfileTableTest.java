package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import org.junit.Test;

public class NpcProfileTableTest {

  private static final String JSON =
      "{"
          + "\"default\":{\"name\":\"Default\",\"accent\":\"British RP.\",\"style\":\"Plain.\",\"pace\":\"Steady.\"},"
          + "\"player\":{\"name\":\"Adventurer\",\"style\":\"Brave hero.\"},"
          + "\"follower\":{\"name\":\"Companion\",\"style\":\"Eager sidekick.\"},"
          + "\"narrator\":{\"name\":\"Narrator\",\"style\":\"Measured storyteller.\"},"
          + "\"byRace\":{"
          + "\"Human\":{\"name\":\"Human\",\"accent\":\"British.\",\"style\":\"Ordinary.\"},"
          + "\"Troll\":{\"name\":\"Troll\",\"accent\":\"Brixton.\",\"style\":\"Big and dim.\"}},"
          + "\"byEthnicity\":{\"kharidian\":{\"accent\":\"Middle Eastern.\"}},"
          + "\"byCategory\":["
          + "{\"id\":\"vampyre\",\"keywords\":[\"vampyre\",\"vyre\"],\"name\":\"Vampyre\",\"accent\":\"Transylvanian.\",\"style\":\"Predatory.\"},"
          + "{\"id\":\"imp\",\"keywords\":[\"imp\"],\"name\":\"Imp\",\"style\":\"Squeaky.\"},"
          + "{\"id\":\"child\",\"keywords\":[\"child\",\"urchin\"],\"lifeStage\":\"child\","
          + "\"style\":\"Bright and young.\"}"
          + "],"
          + "\"byId\":{\"_comment\":\"x\",\"100\":{\"name\":\"Vanstrom\",\"style\":\"An ancient vampyre lord.\"}}"
          + "}";

  private static NpcProfileTable.Resolution resolve(
      NpcProfileTable table, Integer npcId, String npcName, String race, String ethnicity) {
    return table.resolveNpc(npcId, table.matchName(npcName), race, ethnicity, false);
  }

  private static boolean isChild(NpcProfileTable table, String npcName) {
    return table.matchName(npcName).child();
  }

  private static NpcProfileTable table() {
    JsonObject profiles = new JsonParser().parse(JSON).getAsJsonObject();
    return NpcProfileTable.fromProfilesJson(profiles);
  }

  @Test
  public void noMatchingLayerFallsBackToTheCompleteDefault() {
    CharacterProfile p = resolve(table(), null, "Random Bloke", null, null).profile();
    assertEquals("Default", p.name());
    assertEquals("British RP.", p.accent());
    assertEquals("Plain.", p.style());
    assertEquals("Steady.", p.pace());
  }

  @Test
  public void raceLayerOverridesDefaultAndInheritsUnsetFields() {
    NpcProfileTable.Resolution r = resolve(table(), null, "Mountain Troll", "Troll", null);
    assertEquals("race:Troll", r.source());
    assertEquals("Troll", r.profile().name());
    assertEquals("Brixton.", r.profile().accent());
    assertEquals("Big and dim.", r.profile().style());
    assertEquals("pace falls through to the default", "Steady.", r.profile().pace());
  }

  @Test
  public void ethnicityAccentTintsPlainFolkOverTheRaceAccent() {
    NpcProfileTable.Resolution r = resolve(table(), null, "Desert Trader", "Human", "kharidian");
    assertEquals("race:Human+ethnicity:kharidian", r.source());
    assertEquals(
        "the ethnicity accent wins for plain folk", "Middle Eastern.", r.profile().accent());
    assertEquals(
        "style stays the race style (ethnicity is accent-only)", "Ordinary.", r.profile().style());
  }

  @Test
  public void ethnicityIsSkippedForDistinctiveRaces() {
    NpcProfileTable.Resolution r = resolve(table(), null, "Desert Troll", "Troll", "kharidian");
    assertEquals("ethnicity is not applied to a distinctive race", "race:Troll", r.source());
    assertEquals("Brixton.", r.profile().accent());
  }

  @Test
  public void aKeywordCategoryStillBeatsTheEthnicityAccent() {
    NpcProfileTable.Resolution r = resolve(table(), null, "Feral Vampyre", "Human", "kharidian");
    assertEquals("race:Human+ethnicity:kharidian+keyword:vampyre", r.source());
    assertEquals("Transylvanian.", r.profile().accent());
  }

  @Test
  public void raceAndCategoryCombineStyleWhileTheCategoryAccentWins() {
    NpcProfileTable.Resolution r = resolve(table(), null, "Vampyre Brute", "Troll", null);
    assertEquals("race:Troll+keyword:vampyre", r.source());
    assertEquals("the most specific name wins", "Vampyre", r.profile().name());
    assertEquals(
        "the category accent beats the race accent", "Transylvanian.", r.profile().accent());
    assertTrue("the race style is part of the blend", r.profile().style().contains("Big and dim."));
    assertTrue(
        "the category style is part of the blend", r.profile().style().contains("Predatory."));
  }

  @Test
  public void stackedStylesWithoutFullStopsAreSeparatedAsSentences() {
    JsonObject profiles =
        new JsonParser()
            .parse(
                "{\"default\":{\"name\":\"D\",\"accent\":\"British accent\","
                    + "\"style\":\"Plain\",\"pace\":\"Steady pace\"},"
                    + "\"byRace\":{\"Gnome\":{\"style\":\"Chatty and clever\"}},"
                    + "\"byId\":{\"7\":{\"style\":\"Regal and gracious!\"}}}")
            .getAsJsonObject();
    NpcProfileTable table = NpcProfileTable.fromProfilesJson(profiles);

    assertEquals(
        "Chatty and clever. Regal and gracious!",
        resolve(table, 7, "King", "Gnome", null).profile().style());
  }

  private static NpcProfileTable regionTable() {
    JsonObject profiles =
        new JsonParser()
            .parse(
                "{\"default\":{\"name\":\"D\",\"accent\":\"Southern\",\"voiceRegion\":\"SOUTHERN\","
                    + "\"style\":\"Plain\",\"pace\":\"Steady\"},"
                    + "\"byRace\":{\"Dwarf\":{\"accent\":\"Glasgow\",\"voiceRegion\":\"SCOTTISH\","
                    + "\"accentDetail\":\"Gruff Glaswegian.\"},"
                    + "\"Human\":{\"style\":\"An ordinary citizen.\"}},"
                    + "\"byEthnicity\":{\"tirannwn\":{\"accent\":\"Welsh\"},"
                    + "\"wilderness\":{\"accent\":\"Harsh\",\"style\":\"A hardened outlaw.\","
                    + "\"replaceStyle\":true}},"
                    + "\"byId\":{\"9\":{\"style\":\"Gruff\"},\"12\":{\"style\":\"A witch.\"},"
                    + "\"13\":{\"accent\":\"Plain\"}}}")
            .getAsJsonObject();
    return NpcProfileTable.fromProfilesJson(profiles);
  }

  @Test
  public void aChildMarkedByTheTableTakesTheChildLayerWithoutAChildName() {
    NpcProfileTable table = table();
    NpcProfileTable.Resolution named =
        table.resolveNpc(null, table.matchName("Shilop"), "Human", null, true);
    assertTrue(named.source().contains("lifeStage:child"));
    assertTrue(named.profile().style().contains("Bright and young."));
  }

  @Test
  public void aChildNamedAsOneTakesTheChildLayerOnce() {
    NpcProfileTable table = table();
    NpcProfileTable.Resolution keyword =
        table.resolveNpc(null, table.matchName("Street urchin"), "Human", null, true);
    assertTrue(keyword.source().contains("keyword:child"));
    assertFalse(keyword.source().contains("lifeStage:child"));
  }

  @Test
  public void theMostSpecificPitchWins() {
    JsonObject profiles =
        new JsonParser()
            .parse(
                "{\"default\":{\"name\":\"D\",\"accent\":\"A\",\"style\":\"S\",\"pace\":\"P\"},"
                    + "\"byRace\":{\"Goblin\":{\"pitch\":\"High\"}},"
                    + "\"byId\":{\"3\":{\"pitch\":\"Very high\"}}}")
            .getAsJsonObject();
    NpcProfileTable table = NpcProfileTable.fromProfilesJson(profiles);
    assertEquals("High", resolve(table, null, "Goblin", "Goblin", null).profile().pitch());
    assertEquals("Very high", resolve(table, 3, "Goblin", "Goblin", null).profile().pitch());
    assertEquals(null, resolve(table, null, "Man", "Human", null).profile().pitch());
  }

  @Test
  public void theVoiceRegionComesFromTheLayerThatSetTheWinningAccent() {
    assertEquals(
        "SCOTTISH", resolve(regionTable(), 9, "Dwarf", "Dwarf", null).profile().voiceRegion());
  }

  @Test
  public void anAccentWithNoRegionClearsTheRegionItOverrides() {
    CharacterProfile welsh = resolve(regionTable(), null, "Elf", "Human", "tirannwn").profile();
    assertEquals("Welsh", welsh.accent());
    assertEquals(null, welsh.voiceRegion());
  }

  @Test
  public void theAccentDetailComesFromTheLayerThatSetTheWinningAccent() {
    assertEquals(
        "Gruff Glaswegian.",
        resolve(regionTable(), 9, "Dwarf", "Dwarf", null).profile().accentDetail());
  }

  @Test
  public void anAccentWithoutDetailClearsTheDetailItOverrides() {
    assertEquals(null, resolve(regionTable(), 13, "Dwarf", "Dwarf", null).profile().accentDetail());
  }

  @Test
  public void aReplacingStyleDropsTheLessSpecificStylesAndKeepsTheMoreSpecificOnes() {
    CharacterProfile outlaw =
        resolve(regionTable(), 12, "Krystilia", "Human", "wilderness").profile();
    assertEquals("A hardened outlaw. A witch.", outlaw.style());
    assertEquals(
        "An ordinary citizen.",
        resolve(regionTable(), null, "Man", "Human", null).profile().style());
  }

  @Test
  public void anNpcWithNoAccentLayerKeepsTheDefaultRegion() {
    assertEquals(
        "SOUTHERN", resolve(regionTable(), null, "Man", "Human", null).profile().voiceRegion());
  }

  @Test
  public void multipleCategoriesAllCombine() {
    NpcProfileTable.Resolution r = resolve(table(), null, "Imp Vampyre", null, null);
    assertEquals(
        "both categories appear in declaration order", "keyword:vampyre+keyword:imp", r.source());
    assertTrue(r.profile().style().contains("Predatory."));
    assertTrue(r.profile().style().contains("Squeaky."));
    assertEquals("the last category to set a name wins", "Imp", r.profile().name());
  }

  @Test
  public void perIdOverrideAddsOnTopAndWinsSingleValuedFields() {
    NpcProfileTable.Resolution r = resolve(table(), 100, "Vampyre Vanstrom", "Undead", null);
    assertEquals("every match contributes", "keyword:vampyre+id:100", r.source());
    assertEquals("the bespoke name wins", "Vanstrom", r.profile().name());
    assertTrue(
        "the category style is still in the blend", r.profile().style().contains("Predatory."));
    assertTrue(
        "the bespoke style is added on top",
        r.profile().style().contains("An ancient vampyre lord."));
    assertEquals(
        "accent the id entry did not set inherits from the matched keyword layer",
        "Transylvanian.",
        r.profile().accent());
  }

  @Test
  public void raceMatchingIsCaseInsensitive() {
    assertEquals("Troll", resolve(table(), null, "x", "TROLL", null).profile().name());
    assertEquals("Troll", resolve(table(), null, "x", "troll", null).profile().name());
  }

  @Test
  public void keywordMatchingRespectsWordBoundaries() {
    assertEquals(
        "'imp' must not match inside 'important'",
        "Default",
        resolve(table(), null, "Important Person", null, null).profile().name());
    assertEquals(
        "'imp' matches the whole word",
        "Imp",
        resolve(table(), null, "Imp", null, null).profile().name());
  }

  @Test
  public void wordContainsIsBoundedOnBothSides() {
    assertTrue(NpcProfileTable.wordContains("feral vampyre", "vampyre"));
    assertTrue(NpcProfileTable.wordContains("tztok-jad", "jad"));
    assertFalse(NpcProfileTable.wordContains("important", "imp"));
    assertFalse(NpcProfileTable.wordContains("shrimp", "imp"));
  }

  @Test
  public void playerProfileLayersOverDefaultThenConfigOverridesNonBlankFields() {
    NpcProfileTable t = table();

    CharacterProfile base = t.resolvePlayer(null, null, null);
    assertEquals("Adventurer", base.name());
    assertEquals("the player style comes from the player layer", "Brave hero.", base.style());
    assertEquals("accent inherits from the default", "British RP.", base.accent());

    CharacterProfile overridden = t.resolvePlayer("Pirate drawl.", "   ", "");
    assertEquals("a non-blank accent overrides", "Pirate drawl.", overridden.accent());
    assertEquals("a blank style inherits", "Brave hero.", overridden.style());
    assertEquals("a blank pace inherits", "Steady.", overridden.pace());
  }

  @Test
  public void followerProfileLayersOverDefaultThenConfigOverridesNonBlankFields() {
    NpcProfileTable t = table();

    CharacterProfile base = t.resolveFollower(null, null, null, NpcGender.MALE);
    assertEquals("Companion", base.name());
    assertTrue(
        "the follower style comes from the follower layer",
        base.style().startsWith("Eager sidekick."));
    assertEquals("accent inherits from the default", "British RP.", base.accent());

    CharacterProfile overridden = t.resolveFollower("Yorkshire.", "   ", "", NpcGender.MALE);
    assertEquals("a non-blank accent overrides", "Yorkshire.", overridden.accent());
    assertTrue("a blank style inherits", overridden.style().startsWith("Eager sidekick."));
    assertEquals("a blank pace inherits", "Steady.", overridden.pace());
  }

  @Test
  public void followerConfigFieldsAreSanitizedBeforeTheyReachThePrompt() {
    CharacterProfile p =
        table()
            .resolveFollower(
                "Yorkshire.\n\nBroad\r\nand warm.",
                "[angry] gruff <laugh> and loud",
                "Brisk.",
                NpcGender.MALE);

    assertEquals("Yorkshire. Broad and warm.", p.accent());
    assertTrue(p.style().startsWith("angry gruff laugh and loud "));
    assertEquals("Brisk.", p.pace());
  }

  @Test
  public void anOverlongFollowerFieldIsCappedLikeThePlayers() {
    StringBuilder longAccent = new StringBuilder();
    for (int i = 0; i < DirectionSanitizer.MAX_FIELD_LENGTH + 200; i++) {
      longAccent.append('a');
    }

    CharacterProfile p = table().resolveFollower(longAccent.toString(), null, null, NpcGender.MALE);

    assertEquals(DirectionSanitizer.MAX_FIELD_LENGTH, p.accent().length());
  }

  @Test
  public void theFollowerIsNotJustThePlayerWearingADifferentLabel() {
    NpcProfileTable t = table();

    assertNotEquals(
        t.resolvePlayer(null, null, null).cacheKey(),
        t.resolveFollower(null, null, null, NpcGender.MALE).cacheKey());
  }

  @Test
  public void aTableWithNoFollowerLayerStillResolvesTheDefault() {
    JsonObject profiles =
        new JsonParser()
            .parse(
                "{\"default\":{\"name\":\"Default\",\"accent\":\"British RP.\","
                    + "\"style\":\"Plain.\",\"pace\":\"Steady.\"}}")
            .getAsJsonObject();

    CharacterProfile follower =
        NpcProfileTable.fromProfilesJson(profiles)
            .resolveFollower(null, null, null, NpcGender.MALE);

    assertEquals("Default", follower.name());
    assertTrue(follower.style().startsWith("Plain."));
  }

  @Test
  public void theFollowersGenderIsStatedOutrightInItsDirection() {
    NpcProfileTable t = table();

    assertTrue(
        t.resolveFollower(null, null, null, NpcGender.MALE)
            .style()
            .endsWith(NpcProfileTable.MALE_VOICING));
    assertTrue(
        t.resolveFollower(null, null, null, NpcGender.FEMALE)
            .style()
            .endsWith(NpcProfileTable.FEMALE_VOICING));
    assertTrue(
        "an unknown gender falls the same way the voice map does",
        t.resolveFollower(null, null, null, NpcGender.UNKNOWN)
            .style()
            .endsWith(NpcProfileTable.MALE_VOICING));
  }

  @Test
  public void theGenderClauseSurvivesAnOverlongPersona() {
    StringBuilder longStyle = new StringBuilder();
    for (int i = 0; i < DirectionSanitizer.MAX_FIELD_LENGTH + 200; i++) {
      longStyle.append('a');
    }

    CharacterProfile p =
        table().resolveFollower(null, longStyle.toString(), null, NpcGender.FEMALE);

    assertTrue(
        "the clause is ours and is appended after the player's text is capped",
        p.style().endsWith(NpcProfileTable.FEMALE_VOICING));
  }

  @Test
  public void eachFollowerGenderIsItsOwnCachedDirection() {
    NpcProfileTable t = table();

    assertNotEquals(
        t.resolveFollower(null, null, null, NpcGender.MALE).cacheKey(),
        t.resolveFollower(null, null, null, NpcGender.FEMALE).cacheKey());
  }

  @Test
  public void narratorProfileLayersOverDefaultAndTakesNoConfiguredFields() {
    CharacterProfile narrator = table().resolveNarrator();

    assertEquals("Narrator", narrator.name());
    assertEquals(
        "the narrator style comes from the narrator layer",
        "Measured storyteller.",
        narrator.style());
    assertEquals("accent inherits from the default", "British RP.", narrator.accent());
    assertEquals("pace inherits from the default", "Steady.", narrator.pace());
  }

  @Test
  public void narratorProfileIsIdenticalEveryCallSoItsCacheKeyHolds() {
    NpcProfileTable t = table();
    assertEquals(t.resolveNarrator().cacheKey(), t.resolveNarrator().cacheKey());
  }

  @Test
  public void aTableWithNoNarratorLayerStillResolvesTheDefault() {
    JsonObject profiles =
        new JsonParser()
            .parse(
                "{\"default\":{\"name\":\"Default\",\"accent\":\"British RP.\","
                    + "\"style\":\"Plain.\",\"pace\":\"Steady.\"}}")
            .getAsJsonObject();

    CharacterProfile narrator = NpcProfileTable.fromProfilesJson(profiles).resolveNarrator();

    assertEquals("Default", narrator.name());
    assertEquals("Plain.", narrator.style());
  }

  @Test
  public void childAgeCategoryMarksMatchingNamesAsChildren() {
    NpcProfileTable t = table();
    assertTrue("'Child' matches the child category", isChild(t, "Child"));
    assertTrue("'Street urchin' matches the child category", isChild(t, "Street urchin"));
    assertFalse("an adult name is not a child", isChild(t, "Random Bloke"));
    assertFalse("a non-child category match is not a child", isChild(t, "Imp"));
    assertFalse("a null name is not a child", isChild(t, null));
  }

  @Test
  public void childCategoryStyleLayersOverTheRaceStyle() {
    NpcProfileTable.Resolution r = resolve(table(), null, "Troll child", "Troll", null);
    assertEquals("race:Troll+keyword:child", r.source());
    assertEquals("Big and dim. Bright and young.", r.profile().style());
    assertEquals(
        "the child category leaves the accent to the race", "Brixton.", r.profile().accent());
  }
}
