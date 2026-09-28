package com.grahambartley.runelite.voiced.dialogue.panel;

import static com.grahambartley.runelite.voiced.dialogue.panel.PanelFixtures.FALADOR_GUARD;
import static com.grahambartley.runelite.voiced.dialogue.panel.PanelFixtures.GUARD;
import static com.grahambartley.runelite.voiced.dialogue.panel.PanelFixtures.GUARD_CAPTAIN;
import static com.grahambartley.runelite.voiced.dialogue.panel.PanelFixtures.HANS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.profile.NpcProfileTable;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceCatalog;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceOverride;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceOverrideStore;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceScope;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class NpcDetailViewTest {

  private static final List<Integer> GUARD_IDS =
      Arrays.asList(FALADOR_GUARD, 11911, 11912, 11913, 11914, 11915, 11916, 11917);

  private final NpcVoiceOverrideStore store = PanelFixtures.store();
  private final AtomicInteger backs = new AtomicInteger();
  private final AtomicInteger changes = new AtomicInteger();
  private final NpcDetailView view =
      new NpcDetailView(
          PanelFixtures.catalog(),
          store,
          PanelFixtures.offlineChatheads(),
          backs::incrementAndGet,
          changes::incrementAndGet);

  private void openGuard() {
    view.open(new NpcListEntry("Guard", GUARD_IDS, true, false, GUARD));
  }

  private void assertBlank() {
    assertEquals(VoiceTypeOption.PLUGIN_DEFAULT, view.voiceType().getSelectedItem());
    assertEquals("", view.nameField().getText());
    assertEquals("", view.accentField().getText());
    assertEquals("", view.styleField().getText());
    assertEquals("", view.paceField().getText());
  }

  @Test
  public void anUneditedNpcOpensBlankOnPluginDefault() {
    view.open(PanelFixtures.single("Hans", HANS));

    assertEquals("Hans", view.titleText());
    assertBlank();
    assertFalse(view.saveButton().isEnabled());
    assertFalse(view.clearButton().isEnabled());
  }

  @Test
  public void aBundledNpcStillOpensBlankBecauseOnlyThePlayersValuesAreShown() {
    NpcDetailView bundled =
        new NpcDetailView(
            bundledCatalog(), store, PanelFixtures.offlineChatheads(), () -> {}, () -> {});

    bundled.open(PanelFixtures.single("Hans", HANS));

    assertEquals(VoiceTypeOption.PLUGIN_DEFAULT, bundled.voiceType().getSelectedItem());
    assertEquals("", bundled.nameField().getText());
    assertEquals("", bundled.accentField().getText());
    assertEquals("", bundled.styleField().getText());
    assertEquals("", bundled.paceField().getText());
  }

  private static NpcVoiceCatalog bundledCatalog() {
    NpcProfileTable table = new NpcProfileTable();
    table.initialize();
    return table.catalog();
  }

  @Test
  public void anEditedNpcShowsOnlyThePlayersSavedValues() {
    store.set(HANS, new NpcVoiceOverride(null, null, "Nervous", null, VoiceType.TYPE_B));

    view.open(PanelFixtures.single("Hans", HANS));

    assertEquals(VoiceTypeOption.TYPE_B, view.voiceType().getSelectedItem());
    assertEquals("Nervous", view.styleField().getText());
    assertEquals("", view.accentField().getText());
    assertFalse(view.saveButton().isEnabled());
    assertTrue(view.clearButton().isEnabled());
  }

  @Test
  public void savingWithOnlyStyleFilledOverridesOnlyStyle() {
    view.open(PanelFixtures.single("Hans", HANS));
    view.styleField().setText("Nervous and jumpy");

    assertTrue(view.saveButton().isEnabled());
    view.saveButton().doClick();

    assertEquals(
        new NpcVoiceOverride(null, null, "Nervous and jumpy", null, null), store.get(HANS));
    assertEquals("Saved for 1 NPC.", view.statusText());
    assertEquals(1, changes.get());
    assertFalse(view.saveButton().isEnabled());
  }

  @Test
  public void savingAVarrockGuardAsThisCharacterWritesOnlyItsSixIds() {
    openGuard();
    view.accentField().setText("Strong Scottish accent");
    view.scopePicker().select(NpcVoiceScope.THIS_CHARACTER);

    view.saveButton().doClick();

    assertEquals(
        new HashSet<>(Arrays.asList(11911, 11912, 11913, 11914, 11915, 11916)),
        store.overriddenIds());
    assertNull(store.get(GUARD_CAPTAIN));
    assertNull(store.get(FALADOR_GUARD));
    assertEquals("Saved for 6 NPCs.", view.statusText());
  }

  @Test
  public void savingForEveryoneCalledGuardWritesEveryGuard() {
    openGuard();
    view.voiceType().setSelectedItem(VoiceTypeOption.TYPE_B);
    view.scopePicker().select(NpcVoiceScope.SAME_NAME);

    view.saveButton().doClick();

    assertEquals(new HashSet<>(GUARD_IDS), store.overriddenIds());
  }

  @Test
  public void aBroaderScopeEnablesSaveWhenOtherIdsDiffer() {
    store.set(GUARD, new NpcVoiceOverride(null, "Scottish", null, null, null));
    openGuard();
    assertFalse(view.saveButton().isEnabled());

    view.scopePicker().select(NpcVoiceScope.THIS_CHARACTER);
    view.scopePicker().button(NpcVoiceScope.THIS_CHARACTER).doClick();

    assertTrue(view.saveButton().isEnabled());
  }

  @Test
  public void clearRemovesTheOverrideForTheChosenScope() {
    store.set(HANS, new NpcVoiceOverride(null, "Scottish", null, null, null));
    view.open(PanelFixtures.single("Hans", HANS));

    view.clearButton().doClick();

    assertNull(store.get(HANS));
    assertBlank();
    assertEquals("Cleared 1 NPC. The plugin's voice is back.", view.statusText());
    assertFalse(view.clearButton().isEnabled());
    assertEquals(1, changes.get());
  }

  @Test
  public void clearOnlyTouchesIdsInTheScope() {
    store.set(GUARD, new NpcVoiceOverride(null, "Scottish", null, null, null));
    store.set(GUARD_CAPTAIN, new NpcVoiceOverride(null, "Welsh", null, null, null));
    openGuard();
    view.scopePicker().select(NpcVoiceScope.THIS_CHARACTER);

    view.clearButton().doClick();

    assertNull(store.get(GUARD));
    assertEquals("Welsh", store.get(GUARD_CAPTAIN).accent());
  }

  @Test
  public void savingABlankFormClearsTheOverride() {
    store.set(HANS, new NpcVoiceOverride(null, "Scottish", null, null, null));
    view.open(PanelFixtures.single("Hans", HANS));

    view.accentField().setText("   ");
    view.saveButton().doClick();

    assertNull(store.get(HANS));
    assertEquals("Cleared 1 NPC.", view.statusText());
  }

  @Test
  public void aSharedNameOffersTheChoiceOfWhichNpcWithTheHeardOneSelected() {
    openGuard();

    assertTrue(view.npcPickerShown());
    assertEquals(GUARD_IDS.size(), view.npcPicker().getItemCount());
    assertEquals(GUARD, view.npcPicker().getSelectedItem());
  }

  @Test
  public void aSingleNpcHidesTheChoice() {
    view.open(PanelFixtures.single("Hans", HANS));

    assertFalse(view.npcPickerShown());
  }

  @Test
  public void pickingAnotherNpcLoadsItsOwnValues() {
    store.set(GUARD_CAPTAIN, new NpcVoiceOverride(null, null, null, "Brisk", null));
    openGuard();

    view.npcPicker().setSelectedItem(GUARD_CAPTAIN);

    assertEquals("Brisk", view.paceField().getText());
    assertEquals("Only this NPC (1)", view.scopePicker().button(NpcVoiceScope.THIS_NPC).getText());
    assertFalse(view.scopePicker().button(NpcVoiceScope.THIS_CHARACTER).isEnabled());
  }

  @Test
  public void fieldsAreCappedInLength() {
    view.open(PanelFixtures.single("Hans", HANS));
    StringBuilder longText = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      longText.append('x');
    }

    view.nameField().setText(longText.toString());
    view.accentField().setText(longText.toString());
    view.styleField().setText(longText.toString());
    view.paceField().setText(longText.toString());

    assertEquals(NpcDetailView.NAME_LIMIT, view.nameField().getText().length());
    assertEquals(NpcDetailView.ACCENT_LIMIT, view.accentField().getText().length());
    assertEquals(NpcDetailView.STYLE_LIMIT, view.styleField().getText().length());
    assertEquals(NpcDetailView.PACE_LIMIT, view.paceField().getText().length());
  }

  @Test
  public void blankFieldsShowThePluginDefaultPlaceholder() {
    assertEquals(
        NpcDetailView.PLACEHOLDER,
        view.nameField().getClientProperty("JTextField.placeholderText"));
    assertEquals(NpcDetailView.PLACEHOLDER, view.styleField().placeholder());
  }

  @Test
  public void theCopyWarnsThatEditsAreReVoicedAndReBilled() {
    assertTrue(NpcDetailView.REVOICE_NOTE.contains("re-voiced, and re-billed"));
  }

  @Test
  public void reopeningClearsTheLastStatus() {
    view.open(PanelFixtures.single("Hans", HANS));
    view.styleField().setText("Nervous");
    view.saveButton().doClick();

    view.open(PanelFixtures.single("Hans", HANS));

    assertEquals(" ", view.statusText());
  }

  @Test
  public void npcCountReadsNaturally() {
    assertEquals("1 NPC", NpcDetailView.npcCount(1));
    assertEquals("0 NPCs", NpcDetailView.npcCount(0));
    assertEquals("6 NPCs", NpcDetailView.npcCount(6));
  }

  @Test
  public void formOverrideTrimsAndDropsBlanks() {
    view.open(PanelFixtures.single("Hans", HANS));
    view.nameField().setText("  Hans the Elder ");
    view.paceField().setText("   ");
    view.voiceType().setSelectedItem(VoiceTypeOption.TYPE_A);

    assertEquals(
        new NpcVoiceOverride("Hans the Elder", null, null, null, VoiceType.TYPE_A),
        view.formOverride());
  }
}
