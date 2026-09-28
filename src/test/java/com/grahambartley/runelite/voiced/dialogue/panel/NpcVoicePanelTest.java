package com.grahambartley.runelite.voiced.dialogue.panel;

import static com.grahambartley.runelite.voiced.dialogue.panel.PanelFixtures.HANS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceCatalog;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceImportPlan;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceOverride;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceOverrideStore;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceTransferCodec;
import com.grahambartley.runelite.voiced.dialogue.profile.RecentNpcSpeakers;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.junit.Test;

public class NpcVoicePanelTest {

  private final NpcVoiceCatalog catalog = PanelFixtures.catalog();
  private final RecentNpcSpeakers speakers = new RecentNpcSpeakers();
  private final NpcVoiceOverrideStore store = PanelFixtures.store();
  private final List<Set<Integer>> nameRequests = new ArrayList<>();
  private final List<Consumer<Map<Integer, String>>> nameCallbacks = new ArrayList<>();
  private final List<Runnable> uiQueue = new ArrayList<>();
  private boolean showing = true;
  private final PanelFixtures.ScriptedDialogs dialogs = new PanelFixtures.ScriptedDialogs();

  private final NpcVoicePanel panel =
      new NpcVoicePanel(
          catalog,
          speakers,
          store,
          new NpcVoiceTransferCodec(store, new Gson()),
          (ids, done) -> {
            nameRequests.add(ids);
            nameCallbacks.add(done);
          },
          PanelFixtures.offlineChatheads(),
          uiQueue::add,
          () -> showing,
          dialogs);

  private void drainUi() {
    List<Runnable> queued = new ArrayList<>(uiQueue);
    uiQueue.clear();
    queued.forEach(Runnable::run);
  }

  private List<String> rowNames() {
    List<String> names = new ArrayList<>();
    panel.listView().rows().forEach(row -> names.add(row.nameText()));
    return names;
  }

  private static void click(NpcListRow row) {
    row.dispatchEvent(
        new MouseEvent(row, MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false, MouseEvent.BUTTON1));
  }

  @Test
  public void opensOnTheList() {
    panel.onActivate();

    assertEquals(NpcVoicePanel.LIST_CARD, panel.shownCard());
  }

  @Test
  public void hearingAnNpcRedrawsTheListOnTheUiThread() {
    panel.onActivate();

    speakers.record(HANS, "Hans");
    assertTrue(rowNames().isEmpty());
    drainUi();

    assertEquals(Collections.singletonList("Hans"), rowNames());
  }

  @Test
  public void clickingARowOpensTheDetailAndBackReturnsToTheList() {
    speakers.record(HANS, "Hans");
    panel.onActivate();

    click(panel.listView().rows().get(0));

    assertEquals(NpcVoicePanel.DETAIL_CARD, panel.shownCard());
    assertEquals("Hans", panel.detailView().titleText());
  }

  @Test
  public void savingMarksTheRowEditedWhenBackOnTheList() {
    speakers.record(HANS, "Hans");
    panel.onActivate();
    click(panel.listView().rows().get(0));

    panel.detailView().styleField().setText("Jumpy");
    panel.detailView().saveButton().doClick();
    backToList();

    assertEquals("Edited", panel.listView().rows().get(0).badgeText());
  }

  private void backToList() {
    JScrollPane scroll = (JScrollPane) panel.detailView().getComponent(0);
    JPanel form = (JPanel) scroll.getViewport().getView();
    ((JButton) form.getComponent(0)).doClick();
  }

  @Test
  public void backReturnsToTheList() {
    speakers.record(HANS, "Hans");
    panel.onActivate();
    click(panel.listView().rows().get(0));

    backToList();

    assertEquals(NpcVoicePanel.LIST_CARD, panel.shownCard());
  }

  @Test
  public void anEditedNpcWithNoKnownNameIsResolvedOnceAndThenListed() {
    store.set(424242, new NpcVoiceOverride(null, "Welsh", null, null, null));

    panel.onActivate();
    panel.onActivate();

    assertEquals(1, nameRequests.size());
    assertEquals(Collections.singleton(424242), nameRequests.get(0));

    nameCallbacks.get(0).accept(Collections.singletonMap(424242, "Mystery trader"));
    drainUi();

    assertEquals(Collections.singletonList("Mystery trader"), rowNames());
  }

  @Test
  public void aRefreshWhileOnTheDetailLeavesTheListAlone() {
    speakers.record(HANS, "Hans");
    panel.onActivate();
    click(panel.listView().rows().get(0));
    speakers.record(PanelFixtures.BOB, "Bob");

    panel.refreshLater();
    drainUi();

    assertEquals(Collections.singletonList("Hans"), rowNames());
  }

  @Test
  public void theNavigationIconIsDrawn() {
    assertEquals(PanelIcons.NAVIGATION_SIZE, NpcVoicePanel.navigationIcon().getWidth());
  }

  @Test
  public void aHiddenPanelSkipsRedrawsUntilItOpens() {
    showing = false;

    speakers.record(HANS, "Hans");
    drainUi();
    assertTrue(rowNames().isEmpty());

    panel.onActivate();
    assertEquals(Collections.singletonList("Hans"), rowNames());
  }

  @Test
  public void anOverrideChangingUnderAnOpenFormReloadsIt() {
    speakers.record(HANS, "Hans");
    panel.onActivate();
    click(panel.listView().rows().get(0));

    store.set(HANS, new NpcVoiceOverride(null, null, "Synced from another device", null, null));
    panel.refreshLater();
    drainUi();

    assertEquals("Synced from another device", panel.detailView().styleField().getText());
  }

  @Test
  public void anNpcSpeakingNeverDisturbsAFormBeingEdited() {
    speakers.record(HANS, "Hans");
    panel.onActivate();
    click(panel.listView().rows().get(0));
    panel.detailView().styleField().setText("Half typed");

    speakers.record(PanelFixtures.BOB, "Bob");
    drainUi();

    assertEquals("Half typed", panel.detailView().styleField().getText());
  }

  @Test
  public void anOverrideChangeWhileHiddenReloadsTheOpenFormOnReturn() {
    speakers.record(HANS, "Hans");
    panel.onActivate();
    click(panel.listView().rows().get(0));
    showing = false;

    store.set(HANS, new NpcVoiceOverride(null, null, "From the other profile", null, null));
    panel.refreshLater();
    drainUi();
    assertEquals("", panel.detailView().styleField().getText());

    showing = true;
    panel.onActivate();

    assertEquals("From the other profile", panel.detailView().styleField().getText());
  }

  @Test
  public void returningWithNothingChangedKeepsHalfTypedText() {
    speakers.record(HANS, "Hans");
    panel.onActivate();
    click(panel.listView().rows().get(0));
    panel.detailView().styleField().setText("Half typed");

    panel.onActivate();

    assertEquals("Half typed", panel.detailView().styleField().getText());
  }

  @Test
  public void anImportShowsTheImportedNpcsUnderEditedStraightAway() {
    panel.onActivate();
    dialogs.pasted =
        "{\"format\":\"voiced-dialogue-npc-voices\",\"version\":1,"
            + "\"overrides\":{\"3105\":{\"style\":\"Lost\"}}}";
    dialogs.mode = NpcVoiceImportPlan.Mode.MERGE;

    ((JMenuItem) panel.transferBar().importMenu().getComponent(0)).doClick();

    assertEquals(Collections.singletonList("Hans"), rowNames());
    assertEquals(NpcVoicePanel.LIST_CARD, panel.shownCard());
  }
}
