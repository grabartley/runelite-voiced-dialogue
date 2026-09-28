package com.grahambartley.runelite.voiced.dialogue.panel;

import static com.grahambartley.runelite.voiced.dialogue.panel.PanelFixtures.BOB;
import static com.grahambartley.runelite.voiced.dialogue.panel.PanelFixtures.FALADOR_GUARD;
import static com.grahambartley.runelite.voiced.dialogue.panel.PanelFixtures.GUARD;
import static com.grahambartley.runelite.voiced.dialogue.panel.PanelFixtures.HANS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.profile.HeardNpc;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceCatalog;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

public class NpcListEntriesTest {

  private final NpcVoiceCatalog catalog = PanelFixtures.catalog();
  private final NpcListEntries entries = new NpcListEntries(catalog);

  private static List<String> names(List<NpcListEntry> list) {
    return list.stream().map(NpcListEntry::name).collect(Collectors.toList());
  }

  private static Set<Integer> ids(Integer... ids) {
    return new HashSet<>(Arrays.asList(ids));
  }

  @Test
  public void withNoSearchNothingHeardAndNothingEditedTheListIsEmpty() {
    NpcListing listing = entries.build("", Collections.emptyList(), ids());

    assertFalse(listing.searching());
    assertTrue(listing.heard().isEmpty());
    assertTrue(listing.edited().isEmpty());
    assertNull(listing.matches());
  }

  @Test
  public void heardNpcsAreListedNewestFirstOncePerName() {
    List<HeardNpc> heard =
        Arrays.asList(
            new HeardNpc(HANS, "Hans"),
            new HeardNpc(GUARD, "Guard"),
            new HeardNpc(GUARD + 3, "Guard"));

    NpcListing listing = entries.build(null, heard, ids());

    assertEquals(Arrays.asList("Hans", "Guard"), names(listing.heard()));
    NpcListEntry guard = listing.heard().get(1);
    assertTrue(guard.heard());
    assertEquals(GUARD, guard.preferredId());
  }

  @Test
  public void aHeardNpcCarriesEveryIdSharingItsName() {
    NpcListing listing =
        entries.build("", Collections.singletonList(new HeardNpc(GUARD, "Guard")), ids());

    assertEquals(
        Arrays.asList(FALADOR_GUARD, 11911, 11912, 11913, 11914, 11915, 11916, 11917),
        listing.heard().get(0).ids());
  }

  @Test
  public void editedNpcsNotHeardAreListedAlphabeticallyUnderEdited() {
    NpcListing listing =
        entries.build(
            "", Collections.singletonList(new HeardNpc(HANS, "Hans")), ids(HANS, BOB, GUARD));

    assertEquals(Collections.singletonList("Hans"), names(listing.heard()));
    assertEquals(Arrays.asList("Bob", "Guard"), names(listing.edited()));
    assertTrue(listing.edited().get(1).edited());
    assertEquals(GUARD, listing.edited().get(1).preferredId());
  }

  @Test
  public void anEditedIdTheCatalogCannotNameIsReportedForResolving() {
    NpcListing listing = entries.build("", Collections.emptyList(), ids(424242, HANS));

    assertEquals(Collections.singleton(424242), listing.unnamedEditedIds());
    assertEquals(Collections.singletonList("Hans"), names(listing.edited()));
  }

  @Test
  public void searchMatchesEveryKnownNameCaseInsensitively() {
    NpcListing listing = entries.build("GUA", Collections.emptyList(), ids());

    assertTrue(listing.searching());
    assertEquals(Collections.singletonList("Guard"), names(listing.matches()));
    assertFalse(listing.truncated());
  }

  @Test
  public void searchFindsANameOnlyHeardThisSession() {
    catalog.remember(55555, "Wandering stranger");

    NpcListing listing = entries.build("strang", Collections.emptyList(), ids());

    assertEquals(Collections.singletonList("Wandering stranger"), names(listing.matches()));
  }

  @Test
  public void searchRanksHeardThenEditedThenPrefixThenAlphabetical() {
    Map<Integer, String> names = new HashMap<>();
    names.put(1, "Alan");
    names.put(2, "Brian");
    names.put(3, "Nancy");
    names.put(4, "Anna");
    names.put(5, "Dana");
    NpcListEntries ranked = new NpcListEntries(new NpcVoiceCatalog(names, new HashMap<>()));

    NpcListing listing =
        ranked.build("an", Collections.singletonList(new HeardNpc(5, "Dana")), ids(3));

    assertEquals(Arrays.asList("Dana", "Nancy", "Anna", "Alan", "Brian"), names(listing.matches()));
  }

  @Test
  public void searchIsCappedAndSaysSo() {
    Map<Integer, String> names = new HashMap<>();
    for (int i = 0; i < NpcListEntries.MATCH_LIMIT + 10; i++) {
      names.put(i, String.format("Villager %03d", i));
    }
    NpcListEntries many = new NpcListEntries(new NpcVoiceCatalog(names, new HashMap<>()));

    NpcListing listing = many.build("villager", Collections.emptyList(), ids());

    assertEquals(NpcListEntries.MATCH_LIMIT, listing.matches().size());
    assertTrue(listing.truncated());
  }

  @Test
  public void aSearchWithNoMatchesIsAnEmptyMatchList() {
    NpcListing listing = entries.build("zzz", Collections.emptyList(), ids());

    assertTrue(listing.searching());
    assertTrue(listing.matches().isEmpty());
  }

  @Test
  public void aBlankSearchShowsTheDefaultSections() {
    assertFalse(entries.build("   ", Collections.emptyList(), ids()).searching());
  }

  @Test
  public void anUnheardUneditedEntryPrefersItsLowestId() {
    NpcListing listing = entries.build("guard", Collections.emptyList(), ids());

    NpcListEntry guard = listing.matches().get(0);
    assertFalse(guard.heard());
    assertFalse(guard.edited());
    assertEquals(FALADOR_GUARD, guard.preferredId());
  }

  @Test
  public void aHeardIdTheCatalogDoesNotKnowIsStillPartOfItsEntry() {
    NpcListing listing =
        entries.build("", Collections.singletonList(new HeardNpc(99, "Hans")), ids());

    assertTrue(listing.heard().get(0).ids().contains(99));
    assertEquals(99, listing.heard().get(0).preferredId());
  }

  @Test
  public void anNpcOpenedFromTheGamePrefersTheClickedIdAmongItsNamesakes() {
    NpcListEntry entry = entries.forNpc(GUARD + 2, "guard", Collections.emptyList(), ids());

    assertEquals("Guard", entry.name());
    assertEquals(GUARD + 2, entry.preferredId());
    assertTrue(entry.ids().contains(GUARD));
    assertTrue(entry.ids().contains(FALADOR_GUARD));
    assertFalse(entry.heard());
    assertFalse(entry.edited());
  }

  @Test
  public void anNpcOpenedFromTheGameUnderAnUnbundledIdStillListsIt() {
    NpcListEntry entry = entries.forNpc(424242, "Hans", Collections.emptyList(), ids());

    assertEquals(424242, entry.preferredId());
    assertEquals(Arrays.asList(HANS, 424242), entry.ids());
  }

  @Test
  public void anNpcOpenedFromTheGameCarriesItsHeardAndEditedMarks() {
    NpcListEntry entry =
        entries.forNpc(
            HANS, "Hans", Collections.singletonList(new HeardNpc(HANS, "Hans")), ids(HANS));

    assertTrue(entry.heard());
    assertTrue(entry.edited());
  }

  @Test
  public void aNeverSeenNpcOpenedFromTheGameIsItsOwnEntry() {
    NpcListEntry entry = entries.forNpc(515151, "Quiet hermit", Collections.emptyList(), ids());

    assertEquals("Quiet hermit", entry.name());
    assertEquals(Collections.singletonList(515151), entry.ids());
    assertEquals(515151, entry.preferredId());
  }
}
