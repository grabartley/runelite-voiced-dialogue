package com.grahambartley.runelite.voiced.dialogue.panel;

import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import net.runelite.client.ui.ColorScheme;

final class NpcVoiceTransferBar extends JPanel {

  private final JButton exportButton = new JButton("Export");
  private final JButton importButton = new JButton("Import");
  private final JPopupMenu exportMenu = new JPopupMenu();
  private final JPopupMenu importMenu = new JPopupMenu();

  NpcVoiceTransferBar(NpcVoiceTransfer transfer) {
    super(new GridLayout(1, 2, 6, 0));
    setBackground(ColorScheme.DARK_GRAY_COLOR);
    exportMenu.add(item("Copy to clipboard", transfer::exportToClipboard));
    exportMenu.add(item("Save to file...", transfer::exportToFile));
    importMenu.add(item("Paste text...", transfer::importFromText));
    importMenu.add(item("Open file...", transfer::importFromFile));
    bind(exportButton, exportMenu, "Share your NPC voice edits as JSON");
    bind(importButton, importMenu, "Load NPC voice edits from JSON");
    add(exportButton);
    add(importButton);
  }

  private static JMenuItem item(String label, Runnable action) {
    JMenuItem item = new JMenuItem(label);
    item.addActionListener(e -> action.run());
    return item;
  }

  private static void bind(JButton button, JPopupMenu menu, String tooltip) {
    button.setFocusable(false);
    button.setToolTipText(tooltip);
    button.addActionListener(e -> menu.show(button, 0, button.getHeight()));
  }

  JPopupMenu exportMenu() {
    return exportMenu;
  }

  JPopupMenu importMenu() {
    return importMenu;
  }

  JButton exportButton() {
    return exportButton;
  }

  JButton importButton() {
    return importButton;
  }
}
