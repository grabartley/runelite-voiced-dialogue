package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class NpcVoiceCatalogTest {

  private static NpcVoiceCatalog guards() {
    Map<Integer, String> names = new HashMap<>();
    Map<Integer, String> symbols = new HashMap<>();
    String[] guardSymbols = {
      "FAI_VARROCK_GUARD02",
      "FAI_VARROCK_GUARD02_VARIANT01",
      "FAI_VARROCK_GUARD02_VARIANT02",
      "FAI_VARROCK_GUARD02_F",
      "FAI_VARROCK_GUARD02_F_VARIANT01",
      "FAI_VARROCK_GUARD02_F_VARIANT02",
      "FAI_VARROCK_GUARD_CAPTAIN02"
    };
    for (int i = 0; i < guardSymbols.length; i++) {
      names.put(11911 + i, "Guard");
      symbols.put(11911 + i, guardSymbols[i]);
    }
    names.put(3094, "Guard");
    names.put(3105, "Hans");
    names.put(2000, "Guardian of the grove");
    return new NpcVoiceCatalog(names, symbols);
  }

  @Test
  public void editingAVarrockGuardAsThisCharacterCoversOnlyItsSixVariants() {
    NpcProfileTable table = new NpcProfileTable();
    table.initialize();

    assertEquals(
        Arrays.asList(11911, 11912, 11913, 11914, 11915, 11916),
        table.buildCatalog().scopeIds(11911, NpcVoiceScope.THIS_CHARACTER));
  }

  @Test
  public void thisNpcIsOnlyTheIdItself() {
    assertEquals(
        Collections.singletonList(11913), guards().scopeIds(11913, NpcVoiceScope.THIS_NPC));
  }

  @Test
  public void thisCharacterGroupsTheVariantsAndSexesOfOneSymbol() {
    assertEquals(
        Arrays.asList(11911, 11912, 11913, 11914, 11915, 11916),
        guards().scopeIds(11915, NpcVoiceScope.THIS_CHARACTER));
  }

  @Test
  public void aCaptainIsItsOwnCharacterDespiteSharingTheName() {
    assertEquals(
        Collections.singletonList(11917), guards().scopeIds(11917, NpcVoiceScope.THIS_CHARACTER));
  }

  @Test
  public void anIdWithNoSymbolIsItsOwnCharacter() {
    assertEquals(
        Collections.singletonList(3094), guards().scopeIds(3094, NpcVoiceScope.THIS_CHARACTER));
  }

  @Test
  public void sameNameCoversEveryIdWithThatDisplayName() {
    assertEquals(
        Arrays.asList(3094, 11911, 11912, 11913, 11914, 11915, 11916, 11917),
        guards().scopeIds(11911, NpcVoiceScope.SAME_NAME));
  }

  @Test
  public void sameNameForAnUnnamedIdIsTheIdAlone() {
    assertEquals(
        Collections.singletonList(99999), guards().scopeIds(99999, NpcVoiceScope.SAME_NAME));
  }

  @Test
  public void sameNameIncludesIdsOnlyHeardThisSession() {
    NpcVoiceCatalog catalog = guards();
    catalog.remember(50000, "guard");

    assertTrue(catalog.scopeIds(11911, NpcVoiceScope.SAME_NAME).contains(50000));
    assertTrue(catalog.scopeIds(50000, NpcVoiceScope.SAME_NAME).contains(11911));
  }

  @Test
  @Parameters({
    "FAI_VARROCK_GUARD02, FAI_VARROCK_GUARD",
    "FAI_VARROCK_GUARD02_VARIANT01, FAI_VARROCK_GUARD",
    "FAI_VARROCK_GUARD02_F_VARIANT02, FAI_VARROCK_GUARD",
    "FAI_VARROCK_GUARD02_M, FAI_VARROCK_GUARD",
    "MAGIC_CARPET_SELLER3, MAGIC_CARPET_SELLER",
    "FARMING_TOOLS_LEPRECHAUN, FARMING_TOOLS_LEPRECHAUN",
    "FAI_FORESTER, FAI_FORESTER",
    "FAI_MAN_F_1, FAI_MAN_"
  })
  public void characterKeyStripsSexVariantAndDigits(String symbol, String key) {
    assertEquals(key, NpcVoiceCatalog.characterKey(symbol));
  }

  @Test
  public void aBundledNameWinsOverOneRememberedForTheSameId() {
    NpcVoiceCatalog catalog = guards();
    catalog.remember(3105, "Somebody else");

    assertEquals("Hans", catalog.nameOf(3105));
  }

  @Test
  public void aRememberedNameNamesAnIdTheBundleDoesNot() {
    NpcVoiceCatalog catalog = guards();
    assertNull(catalog.nameOf(77));

    catalog.remember(77, "  Bob  ");

    assertEquals("Bob", catalog.nameOf(77));
  }

  @Test
  public void aBlankNameIsNotRemembered() {
    NpcVoiceCatalog catalog = guards();
    catalog.remember(77, " ");
    catalog.remember(78, null);

    assertNull(catalog.nameOf(77));
    assertNull(catalog.nameOf(78));
  }

  @Test
  public void namesMatchingIsCaseInsensitiveAndDeduplicated() {
    assertEquals(Arrays.asList("Guard", "Guardian of the grove"), guards().namesMatching("GUAR"));
  }

  @Test
  public void namesMatchingFindsSessionNames() {
    NpcVoiceCatalog catalog = guards();
    catalog.remember(77, "Bob");

    assertEquals(Collections.singletonList("Bob"), catalog.namesMatching("bo"));
  }

  @Test
  public void displayNameUsesTheBundledCasing() {
    assertEquals("Guard", guards().displayName("gUARD "));
    assertEquals("Stranger", guards().displayName(" Stranger"));
  }

  @Test
  public void idsNamedIsSortedAndCaseInsensitive() {
    List<Integer> ids = guards().idsNamed("hans");
    assertEquals(Collections.singletonList(3105), ids);
  }
}
