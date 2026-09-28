package com.grahambartley.runelite.voiced.dialogue.panel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceImport;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceImportPlan;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceOverride;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceOverrideStore;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceTransferCodec;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NpcVoiceTransferTest {

  private static final NpcVoiceOverride SCOT =
      new NpcVoiceOverride(null, "Strong Scottish accent", null, null, VoiceType.TYPE_B);
  private static final NpcVoiceOverride LOST =
      new NpcVoiceOverride("Hans", null, "Lost", "Slow", null);

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private final NpcVoiceOverrideStore store = PanelFixtures.store();
  private final NpcVoiceTransferCodec codec = new NpcVoiceTransferCodec(store, new Gson());
  private final PanelFixtures.ScriptedDialogs dialogs = new PanelFixtures.ScriptedDialogs();
  private int refreshes;
  private final NpcVoiceTransfer transfer =
      new NpcVoiceTransfer(store, codec, dialogs, () -> refreshes++);

  private static String document(String overrides) {
    return "{\"format\":\"voiced-dialogue-npc-voices\",\"version\":1,\"overrides\":"
        + overrides
        + "}";
  }

  private Map<Integer, NpcVoiceOverride> twoEdits() {
    store.set(11911, SCOT);
    store.set(3105, LOST);
    Map<Integer, NpcVoiceOverride> expected = new HashMap<>();
    expected.put(11911, SCOT);
    expected.put(3105, LOST);
    return expected;
  }

  private void clearAll() {
    store.overriddenIds().forEach(store::clear);
  }

  @Test
  public void copyingPutsEveryStoredEditOnTheClipboard() throws Exception {
    Map<Integer, NpcVoiceOverride> expected = twoEdits();

    transfer.exportToClipboard();

    assertEquals(expected, codec.decode(dialogs.copied.get(0)).overrides());
    assertEquals(
        Collections.singletonList("Copied edits for 2 NPCs to the clipboard."), dialogs.infos);
  }

  @Test
  public void copyingWithNoEditsStillCopiesAValidDocument() throws Exception {
    transfer.exportToClipboard();

    assertTrue(codec.decode(dialogs.copied.get(0)).overrides().isEmpty());
  }

  @Test
  public void exportClearImportRestoresTheSameOverrides() {
    Map<Integer, NpcVoiceOverride> expected = twoEdits();
    transfer.exportToClipboard();
    clearAll();

    dialogs.pasted = dialogs.copied.get(0);
    dialogs.mode = NpcVoiceImportPlan.Mode.MERGE;
    transfer.importFromText();

    assertEquals(expected, store.all());
  }

  @Test
  public void savingWritesTheDocumentAndAddsTheJsonExtension() throws Exception {
    Map<Integer, NpcVoiceOverride> expected = twoEdits();
    dialogs.exportFile = folder.getRoot().toPath().resolve("my voices");

    transfer.exportToFile();

    Path written = folder.getRoot().toPath().resolve("my voices.json");
    String text = new String(Files.readAllBytes(written), StandardCharsets.UTF_8);
    assertEquals(expected, codec.decode(text).overrides());
    assertEquals(
        Collections.singletonList("Saved edits for 2 NPCs to my voices.json."), dialogs.infos);
  }

  @Test
  public void cancellingTheSaveWritesNothing() {
    transfer.exportToFile();

    assertTrue(dialogs.infos.isEmpty());
    assertTrue(dialogs.errors.isEmpty());
  }

  @Test
  public void aFailedSaveReportsAnError() {
    dialogs.exportFile = folder.getRoot().toPath().resolve("missing").resolve("voices.json");

    transfer.exportToFile();

    assertTrue(dialogs.errors.get(0).startsWith("Could not save voices.json"));
    assertTrue(dialogs.infos.isEmpty());
  }

  @Test
  public void openingAFileImportsIt() throws IOException {
    Path file = folder.newFile("voices.json").toPath();
    Files.write(file, document("{\"3105\":{\"style\":\"Lost\"}}").getBytes(StandardCharsets.UTF_8));
    dialogs.importFile = file;
    dialogs.mode = NpcVoiceImportPlan.Mode.MERGE;

    transfer.importFromFile();

    assertEquals(new NpcVoiceOverride(null, null, "Lost", null, null), store.get(3105));
    assertEquals(1, refreshes);
    assertEquals(
        Collections.singletonList(
            "Imported voices for 1 NPC. Imported NPCs are re-voiced, and re-billed, the next time"
                + " you hear them."),
        dialogs.infos);
  }

  @Test
  public void anOversizedFileIsRefusedUnread() throws IOException {
    Path file = folder.newFile("huge.json").toPath();
    Files.write(file, new byte[(int) NpcVoiceTransfer.MAX_IMPORT_BYTES + 1]);
    dialogs.importFile = file;

    transfer.importFromFile();

    assertEquals(
        Collections.singletonList("huge.json is too large to be an NPC voices file."),
        dialogs.errors);
    assertTrue(dialogs.summaries.isEmpty());
  }

  @Test
  public void anUnreadableFileReportsAnError() {
    dialogs.importFile = folder.getRoot().toPath().resolve("gone.json");

    transfer.importFromFile();

    assertTrue(dialogs.errors.get(0).startsWith("Could not read gone.json"));
  }

  @Test
  public void cancellingThePasteOrTheFileDoesNothing() {
    transfer.importFromText();
    transfer.importFromFile();

    assertTrue(dialogs.summaries.isEmpty());
    assertTrue(dialogs.errors.isEmpty());
    assertEquals(0, refreshes);
  }

  @Test
  public void aMalformedDocumentWritesNothingAndSaysWhy() {
    store.set(1, LOST);
    dialogs.pasted = "{\"format\":\"voiced-dialogue-npc-voices\",\"version\":9,\"overrides\":{}}";
    dialogs.mode = NpcVoiceImportPlan.Mode.REPLACE_ALL;

    transfer.importFromText();

    assertEquals(Collections.singleton(1), store.overriddenIds());
    assertTrue(dialogs.summaries.isEmpty());
    assertTrue(dialogs.errors.get(0).endsWith("Nothing was imported."));
    assertEquals(0, refreshes);
  }

  @Test
  public void aDocumentWithNoValidEntriesWritesNothing() {
    store.set(1, LOST);
    dialogs.pasted = document("{\"abc\":{\"style\":\"Lost\"}}");
    dialogs.mode = NpcVoiceImportPlan.Mode.REPLACE_ALL;

    transfer.importFromText();

    assertEquals(Collections.singleton(1), store.overriddenIds());
    assertEquals(
        Collections.singletonList(
            "There are no NPC voices to import. 1 entry was skipped as invalid."),
        dialogs.errors);
  }

  @Test
  public void cancellingTheConfirmWritesNothing() {
    dialogs.pasted = document("{\"3105\":{\"style\":\"Lost\"}}");

    transfer.importFromText();

    assertEquals(1, dialogs.summaries.size());
    assertTrue(store.overriddenIds().isEmpty());
    assertEquals(0, refreshes);
  }

  @Test
  public void mergeKeepsEditsNotInTheFile() {
    store.set(1, LOST);
    dialogs.pasted = document("{\"2\":{\"style\":\"Lost\"}}");
    dialogs.mode = NpcVoiceImportPlan.Mode.MERGE;

    transfer.importFromText();

    assertEquals(new HashSet<>(Arrays.asList(1, 2)), store.overriddenIds());
  }

  @Test
  public void replaceAllLeavesOnlyTheImportedIds() {
    store.set(1, LOST);
    dialogs.pasted = document("{\"2\":{\"style\":\"Lost\"}}");
    dialogs.mode = NpcVoiceImportPlan.Mode.REPLACE_ALL;

    transfer.importFromText();

    assertEquals(Collections.singleton(2), store.overriddenIds());
  }

  private static NpcVoiceImportPlan plan(
      int skipped, Iterable<Integer> current, Integer... importedIds) {
    Map<Integer, NpcVoiceOverride> overrides = new HashMap<>();
    for (Integer id : importedIds) {
      overrides.put(id, LOST);
    }
    HashSet<Integer> currentIds = new HashSet<>();
    current.forEach(currentIds::add);
    return new NpcVoiceImportPlan(new NpcVoiceImport(overrides, skipped), currentIds);
  }

  @Test
  public void theSummaryCountsSetsReplacesSkipsAndClears() {
    assertEquals(
        "This sets voices for 3 NPCs. 2 of them already have an edit it will replace. 4 entries"
            + " were skipped as invalid.\n\n"
            + "Merge keeps your other edits. Replace all also clears your 1 other edit.",
        NpcVoiceTransfer.summary(plan(4, Arrays.asList(1, 2, 3), 2, 3, 7)));
  }

  @Test
  public void theSummaryLeavesOutWhatDoesNotApply() {
    assertEquals(
        "This sets voices for 1 NPC.\n\n"
            + "Merge keeps your other edits. Replace all has no other edits to clear.",
        NpcVoiceTransfer.summary(plan(0, Collections.emptyList(), 5)));
  }

  @Test
  public void theSummaryUsesSingularWordingForOne() {
    assertEquals(
        "This sets voices for 2 NPCs. 1 of them already has an edit it will replace. 1 entry was"
            + " skipped as invalid.\n\n"
            + "Merge keeps your other edits. Replace all also clears your 2 other edits.",
        NpcVoiceTransfer.summary(plan(1, Arrays.asList(1, 8, 9), 1, 5)));
  }

  @Test
  public void aJsonExtensionIsAddedOnlyWhenMissing() {
    Path dir = folder.getRoot().toPath();

    assertEquals(dir.resolve("a.json"), NpcVoiceTransfer.withJsonExtension(dir.resolve("a")));
    assertEquals(dir.resolve("b.JSON"), NpcVoiceTransfer.withJsonExtension(dir.resolve("b.JSON")));
    assertEquals(
        dir.resolve("c.txt.json"), NpcVoiceTransfer.withJsonExtension(dir.resolve("c.txt")));
  }

  @Test
  public void anImportedHandEditIsSanitized() {
    dialogs.pasted = document("{\"5\":{\"accent\":\"<speak>Irish</speak>\\n[laugh]\"}}");
    dialogs.mode = NpcVoiceImportPlan.Mode.MERGE;

    transfer.importFromText();

    assertFalse(store.get(5).accent().contains("<"));
    assertFalse(store.get(5).accent().contains("["));
  }
}
