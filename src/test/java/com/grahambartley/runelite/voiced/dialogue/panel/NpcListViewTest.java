package com.grahambartley.runelite.voiced.dialogue.panel;

import static com.grahambartley.runelite.voiced.dialogue.panel.PanelFixtures.BOB;
import static com.grahambartley.runelite.voiced.dialogue.panel.PanelFixtures.GUARD;
import static com.grahambartley.runelite.voiced.dialogue.panel.PanelFixtures.HANS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.profile.HeardNpc;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceCatalog;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class NpcListViewTest {

  private final NpcVoiceCatalog catalog = PanelFixtures.catalog();
  private final List<HeardNpc> heard = new ArrayList<>();
  private final Set<Integer> edited = new HashSet<>();
  private final List<NpcListEntry> opened = new ArrayList<>();
  private final List<Set<Integer>> unnamedRequests = new ArrayList<>();

  private final JPanel actions = new JPanel();

  private final NpcListView view =
      new NpcListView(
          new NpcListEntries(catalog),
          () -> heard,
          () -> edited,
          PanelFixtures.offlineChatheads(),
          opened::add,
          unnamedRequests::add,
          actions);

  private List<String> rowNames() {
    return view.rows().stream().map(NpcListRow::nameText).collect(Collectors.toList());
  }

  private static void query(NpcListView listView, String text) throws Exception {
    SwingUtilities.invokeAndWait(() -> listView.setQuery(text));
  }

  @Test
  public void theActionsStayOnTheListWhetherOrNotASearchIsActive() throws Exception {
    view.refresh();
    assertTrue(SwingUtilities.isDescendingFrom(actions, view));

    query(view, "guard");
    assertTrue(SwingUtilities.isDescendingFrom(actions, view));
    assertTrue(actions.isVisible());
  }

  @Test
  public void anEmptyListExplainsHowNpcsGetThere() {
    view.refresh();

    assertTrue(view.rows().isEmpty());
    assertEquals(Collections.singletonList(NpcListView.EMPTY_HINT), view.notes());
  }

  @Test
  public void anNpcSpokenToThisSessionAppearsInTheList() {
    heard.add(new HeardNpc(HANS, "Hans"));

    view.refresh();

    assertEquals(Collections.singletonList("Hans"), rowNames());
  }

  @Test
  public void heardNpcsComeBeforeEditedOnes() {
    heard.add(new HeardNpc(HANS, "Hans"));
    edited.add(BOB);
    edited.add(GUARD);

    view.refresh();

    assertEquals(Arrays.asList("Hans", "Bob", "Guard"), rowNames());
  }

  @Test
  public void searchingListsMatchesAcrossTheBundledNames() throws Exception {
    heard.add(new HeardNpc(HANS, "Hans"));

    query(view, "gua");

    assertEquals(Collections.singletonList("Guard"), rowNames());
  }

  @Test
  public void clearingTheSearchRestoresTheSections() throws Exception {
    heard.add(new HeardNpc(HANS, "Hans"));
    query(view, "gua");

    query(view, "");

    assertEquals(Collections.singletonList("Hans"), rowNames());
  }

  @Test
  public void aSearchWithNoMatchesSaysSo() throws Exception {
    query(view, "zzz");

    assertTrue(view.rows().isEmpty());
    assertEquals(Collections.singletonList("No NPCs match \"zzz\"."), view.notes());
  }

  @Test
  public void aCappedSearchSaysToKeepTyping() throws Exception {
    Map<Integer, String> names = new HashMap<>();
    for (int i = 0; i < NpcListEntries.MATCH_LIMIT + 1; i++) {
      names.put(i, "Villager " + i);
    }
    NpcListView many =
        new NpcListView(
            new NpcListEntries(new NpcVoiceCatalog(names, new HashMap<>())),
            Collections::emptyList,
            Collections::emptySet,
            PanelFixtures.offlineChatheads(),
            e -> {},
            ids -> {},
            new JPanel());

    query(many, "villager");

    assertEquals(NpcListEntries.MATCH_LIMIT, many.rows().size());
    assertEquals(1, many.notes().size());
    assertTrue(many.notes().get(0).startsWith("Showing the first"));
  }

  @Test
  public void clickingARowOpensIt() {
    heard.add(new HeardNpc(HANS, "Hans"));
    view.refresh();
    NpcListRow row = view.rows().get(0);

    row.dispatchEvent(
        new MouseEvent(row, MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false, MouseEvent.BUTTON1));

    assertEquals("Hans", opened.get(0).name());
  }

  @Test
  public void editedNpcsWithNoKnownNameAreAskedFor() {
    edited.add(424242);

    view.refresh();

    assertEquals(Collections.singletonList(Collections.singleton(424242)), unnamedRequests);
  }

  @Test
  public void everyNpcIsNamedSoNothingIsAskedFor() {
    edited.add(HANS);

    view.refresh();

    assertTrue(unnamedRequests.isEmpty());
  }
}
