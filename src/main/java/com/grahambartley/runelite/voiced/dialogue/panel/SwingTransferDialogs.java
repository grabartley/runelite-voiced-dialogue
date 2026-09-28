package com.grahambartley.runelite.voiced.dialogue.panel;

import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceImportPlan;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileNameExtensionFilter;

final class SwingTransferDialogs implements NpcVoiceTransfer.Dialogs {

  static final String TITLE = "NPC Voices";
  static final String DEFAULT_FILE_NAME = "npc-voices.json";

  private final Component parent;

  SwingTransferDialogs(Component parent) {
    this.parent = parent;
  }

  @Override
  public void copyToClipboard(String text) {
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
  }

  @Override
  public Path chooseExportFile() {
    JFileChooser chooser = chooser();
    chooser.setDialogTitle("Export NPC voices");
    chooser.setSelectedFile(new File(DEFAULT_FILE_NAME));
    while (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
      Path target = NpcVoiceTransfer.withJsonExtension(chooser.getSelectedFile().toPath());
      if (!target.toFile().exists() || confirmOverwrite(target)) {
        return target;
      }
    }
    return null;
  }

  @Override
  public String pasteImport() {
    JTextArea text = new JTextArea(12, 28);
    text.setLineWrap(true);
    JScrollPane scroll = new JScrollPane(text);
    int choice =
        JOptionPane.showConfirmDialog(
            parent,
            new Object[] {"Paste an exported NPC voices file:", scroll},
            "Import NPC voices",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
    return choice == JOptionPane.OK_OPTION ? text.getText() : null;
  }

  @Override
  public Path chooseImportFile() {
    JFileChooser chooser = chooser();
    chooser.setDialogTitle("Import NPC voices");
    return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
        ? chooser.getSelectedFile().toPath()
        : null;
  }

  @Override
  public NpcVoiceImportPlan.Mode confirmImport(String summary) {
    String[] options = {"Merge", "Replace all", "Cancel"};
    int choice =
        JOptionPane.showOptionDialog(
            parent,
            summary,
            "Import NPC voices",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);
    if (choice == 0) {
      return NpcVoiceImportPlan.Mode.MERGE;
    }
    return choice == 1 ? NpcVoiceImportPlan.Mode.REPLACE_ALL : null;
  }

  @Override
  public void info(String message) {
    JOptionPane.showMessageDialog(parent, message, TITLE, JOptionPane.INFORMATION_MESSAGE);
  }

  @Override
  public void error(String message) {
    JOptionPane.showMessageDialog(parent, message, TITLE, JOptionPane.ERROR_MESSAGE);
  }

  private boolean confirmOverwrite(Path target) {
    return JOptionPane.showConfirmDialog(
            parent,
            target.getFileName() + " already exists. Replace it?",
            TITLE,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE)
        == JOptionPane.YES_OPTION;
  }

  private static JFileChooser chooser() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
    return chooser;
  }
}
