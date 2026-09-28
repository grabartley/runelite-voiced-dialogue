package com.grahambartley.runelite.voiced.dialogue.panel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceImportPlan;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceOverrideStore;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceTransferCodec;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.Test;

public class NpcVoiceTransferBarTest {

  private final NpcVoiceOverrideStore store = PanelFixtures.store();
  private final PanelFixtures.ScriptedDialogs dialogs = new PanelFixtures.ScriptedDialogs();
  private final NpcVoiceTransferBar bar =
      new NpcVoiceTransferBar(
          new NpcVoiceTransfer(
              store, new NpcVoiceTransferCodec(store::sanitize, new Gson()), dialogs, () -> {}));

  private static List<String> labels(JPopupMenu menu) {
    List<String> labels = new ArrayList<>();
    for (int i = 0; i < menu.getComponentCount(); i++) {
      labels.add(((JMenuItem) menu.getComponent(i)).getText());
    }
    return labels;
  }

  private static void pick(JPopupMenu menu, int index) {
    ((JMenuItem) menu.getComponent(index)).doClick();
  }

  @Test
  public void offersBothButtons() {
    assertEquals("Export", bar.exportButton().getText());
    assertEquals("Import", bar.importButton().getText());
  }

  @Test
  public void exportOffersClipboardOrFile() {
    assertEquals(List.of("Copy to clipboard", "Save to file..."), labels(bar.exportMenu()));
  }

  @Test
  public void importOffersPasteOrFile() {
    assertEquals(List.of("Paste text...", "Open file..."), labels(bar.importMenu()));
  }

  @Test
  public void copyToClipboardExports() {
    pick(bar.exportMenu(), 0);

    assertEquals(1, dialogs.copied.size());
  }

  @Test
  public void saveToFileAsksForAFile() {
    pick(bar.exportMenu(), 1);

    assertTrue(dialogs.copied.isEmpty());
    assertTrue(dialogs.errors.isEmpty());
  }

  @Test
  public void pasteTextImportsThePastedDocument() {
    dialogs.pasted =
        "{\"format\":\"voiced-dialogue-npc-voices\",\"version\":1,"
            + "\"overrides\":{\"7\":{\"pace\":\"Fast\"}}}";
    dialogs.mode = NpcVoiceImportPlan.Mode.MERGE;

    pick(bar.importMenu(), 0);

    assertEquals("Fast", store.get(7).pace());
  }

  @Test
  public void openFileAsksForAFile() {
    pick(bar.importMenu(), 1);

    assertTrue(dialogs.summaries.isEmpty());
    assertTrue(dialogs.errors.isEmpty());
  }
}
