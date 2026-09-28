package com.grahambartley.runelite.voiced.dialogue.panel;

import static com.grahambartley.runelite.voiced.dialogue.panel.NpcDetailView.npcCount;

import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceImport;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceImportPlan;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceOverride;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceOverrideStore;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceTransferCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

final class NpcVoiceTransfer {

  static final long MAX_IMPORT_BYTES = 1_000_000;
  static final String EXTENSION = ".json";
  static final String REVOICE_NOTE =
      "Imported NPCs are re-voiced, and re-billed, the next time you hear them.";

  interface Dialogs {
    void copyToClipboard(String text);

    Path chooseExportFile();

    String pasteImport();

    Path chooseImportFile();

    NpcVoiceImportPlan.Mode confirmImport(String summary);

    void info(String message);

    void error(String message);
  }

  private final NpcVoiceOverrideStore store;
  private final NpcVoiceTransferCodec codec;
  private final Dialogs dialogs;
  private final Runnable onImported;

  NpcVoiceTransfer(
      NpcVoiceOverrideStore store,
      NpcVoiceTransferCodec codec,
      Dialogs dialogs,
      Runnable onImported) {
    this.store = store;
    this.codec = codec;
    this.dialogs = dialogs;
    this.onImported = onImported;
  }

  void exportToClipboard() {
    Map<Integer, NpcVoiceOverride> overrides = store.all();
    dialogs.copyToClipboard(codec.encode(overrides));
    dialogs.info("Copied edits for " + npcCount(overrides.size()) + " to the clipboard.");
  }

  void exportToFile() {
    Path chosen = dialogs.chooseExportFile();
    if (chosen == null) {
      return;
    }
    Path target = withJsonExtension(chosen);
    Map<Integer, NpcVoiceOverride> overrides = store.all();
    try {
      Files.write(target, codec.encode(overrides).getBytes(StandardCharsets.UTF_8));
    } catch (IOException | RuntimeException e) {
      dialogs.error("Could not save " + target.getFileName() + ": " + e.getMessage());
      return;
    }
    dialogs.info(
        "Saved edits for " + npcCount(overrides.size()) + " to " + target.getFileName() + ".");
  }

  void importFromText() {
    String text = dialogs.pasteImport();
    if (text != null) {
      importDocument(text);
    }
  }

  void importFromFile() {
    Path chosen = dialogs.chooseImportFile();
    if (chosen == null) {
      return;
    }
    String text;
    try {
      if (Files.size(chosen) > MAX_IMPORT_BYTES) {
        dialogs.error(chosen.getFileName() + " is too large to be an NPC voices file.");
        return;
      }
      text = new String(Files.readAllBytes(chosen), StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      dialogs.error("Could not read " + chosen.getFileName() + ": " + e.getMessage());
      return;
    }
    importDocument(text);
  }

  private void importDocument(String text) {
    NpcVoiceImport imported;
    try {
      imported = codec.decode(text);
    } catch (NpcVoiceTransferCodec.InvalidDocumentException e) {
      dialogs.error(e.getMessage() + " Nothing was imported.");
      return;
    }
    NpcVoiceImportPlan plan = new NpcVoiceImportPlan(imported, store.overriddenIds());
    if (plan.sets() == 0) {
      dialogs.error("There are no NPC voices to import." + skippedNote(plan.skipped()));
      return;
    }
    NpcVoiceImportPlan.Mode mode = dialogs.confirmImport(summary(plan));
    if (mode == null) {
      return;
    }
    plan.apply(store, mode);
    onImported.run();
    dialogs.info("Imported voices for " + npcCount(plan.sets()) + ". " + REVOICE_NOTE);
  }

  static String summary(NpcVoiceImportPlan plan) {
    StringBuilder summary =
        new StringBuilder("This sets voices for ").append(npcCount(plan.sets())).append('.');
    if (plan.replaces() > 0) {
      summary
          .append(' ')
          .append(
              plan.replaces() == 1
                  ? "1 of them already has"
                  : plan.replaces() + " of them already have")
          .append(" an edit it will replace.");
    }
    summary.append(skippedNote(plan.skipped()));
    summary.append("\n\nMerge keeps your other edits.");
    summary.append(
        plan.clearedByReplaceAll() == 0
            ? " Replace all has no other edits to clear."
            : " Replace all also clears your "
                + plan.clearedByReplaceAll()
                + (plan.clearedByReplaceAll() == 1 ? " other edit." : " other edits."));
    return summary.toString();
  }

  static Path withJsonExtension(Path chosen) {
    String name = chosen.getFileName().toString();
    return name.toLowerCase(Locale.ROOT).endsWith(EXTENSION)
        ? chosen
        : chosen.resolveSibling(name + EXTENSION);
  }

  private static String skippedNote(int skipped) {
    if (skipped == 0) {
      return "";
    }
    return skipped == 1
        ? " 1 entry was skipped as invalid."
        : " " + skipped + " entries were skipped as invalid.";
  }
}
