package com.grahambartley.runelite.voiced.dialogue.panel;

import com.grahambartley.runelite.voiced.dialogue.profile.HeardNpc;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

final class NpcListView extends JPanel {

  static final String EMPTY_HINT =
      "NPCs you hear appear here. Search to find any other NPC by name.";

  private final NpcListEntries entries;
  private final Supplier<List<HeardNpc>> heard;
  private final Supplier<Set<Integer>> edited;
  private final ChatheadImages chatheads;
  private final Consumer<NpcListEntry> onOpen;
  private final Consumer<Set<Integer>> onUnnamedEdits;

  private final IconTextField search = new IconTextField();
  private final JPanel results = new JPanel(new GridBagLayout());
  private final JScrollPane scroll;
  private final List<NpcListRow> rows = new ArrayList<>();
  private final List<String> notes = new ArrayList<>();
  private int row;

  NpcListView(
      NpcListEntries entries,
      Supplier<List<HeardNpc>> heard,
      Supplier<Set<Integer>> edited,
      ChatheadImages chatheads,
      Consumer<NpcListEntry> onOpen,
      Consumer<Set<Integer>> onUnnamedEdits,
      JComponent actions) {
    super(new BorderLayout(0, 8));
    this.entries = entries;
    this.heard = heard;
    this.edited = edited;
    this.chatheads = chatheads;
    this.onOpen = onOpen;
    this.onUnnamedEdits = onUnnamedEdits;
    setBackground(ColorScheme.DARK_GRAY_COLOR);

    search.setIcon(IconTextField.Icon.SEARCH);
    search.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
    search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
    search.setToolTipText("Search every NPC by name");
    search.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyReleased(KeyEvent e) {
            refresh();
          }
        });
    search.addClearListener(this::refresh);
    JPanel top = new JPanel(new BorderLayout(0, 6));
    top.setOpaque(false);
    top.add(search, BorderLayout.NORTH);
    top.add(actions, BorderLayout.SOUTH);
    add(top, BorderLayout.NORTH);

    results.setBackground(ColorScheme.DARK_GRAY_COLOR);
    JPanel pinnedTop = new ViewportWidthPanel(new BorderLayout());
    pinnedTop.setBackground(ColorScheme.DARK_GRAY_COLOR);
    pinnedTop.add(results, BorderLayout.NORTH);
    scroll = new JScrollPane(pinnedTop);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(null);
    add(scroll, BorderLayout.CENTER);
  }

  void refresh() {
    int scrollValue = scroll.getVerticalScrollBar().getValue();
    NpcListing listing = entries.build(search.getText(), heard.get(), edited.get());
    results.removeAll();
    chatheads.newBatch();
    rows.clear();
    notes.clear();
    row = 0;
    if (listing.searching()) {
      showMatches(listing);
    } else {
      showDefault(listing);
    }
    results.revalidate();
    results.repaint();
    SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(scrollValue));
    if (!listing.unnamedEditedIds().isEmpty()) {
      onUnnamedEdits.accept(listing.unnamedEditedIds());
    }
  }

  private void showDefault(NpcListing listing) {
    if (listing.heard().isEmpty() && listing.edited().isEmpty()) {
      addNote(EMPTY_HINT);
      return;
    }
    addSection("Heard this session", listing.heard());
    addSection("Edited", listing.edited());
  }

  private void showMatches(NpcListing listing) {
    if (listing.matches().isEmpty()) {
      addNote("No NPCs match \"" + search.getText().trim() + "\".");
      return;
    }
    listing.matches().forEach(this::addRow);
    if (listing.truncated()) {
      addNote(
          "Showing the first "
              + NpcListEntries.MATCH_LIMIT
              + " matches. Keep typing to narrow it down.");
    }
  }

  private void addSection(String title, List<NpcListEntry> sectionEntries) {
    if (sectionEntries.isEmpty()) {
      return;
    }
    JLabel header = new JLabel(title);
    header.setFont(FontManager.getRunescapeBoldFont());
    header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    header.setBorder(new EmptyBorder(row == 0 ? 0 : 8, 2, 2, 0));
    addComponent(header);
    sectionEntries.forEach(this::addRow);
  }

  private void addRow(NpcListEntry entry) {
    NpcListRow listRow = new NpcListRow(entry, chatheads, onOpen);
    rows.add(listRow);
    addComponent(listRow);
  }

  private void addNote(String text) {
    WrappingLabel note = new WrappingLabel(text, ColorScheme.LIGHT_GRAY_COLOR);
    note.setBorder(new EmptyBorder(8, 2, 0, 0));
    notes.add(text);
    addComponent(note);
  }

  private void addComponent(JComponent component) {
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = row++;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = new Insets(0, 0, 4, 0);
    results.add(component, c);
  }

  void setQuery(String query) {
    search.setText(query);
    refresh();
  }

  void focusSearch() {
    search.requestFocusInWindow();
  }

  List<NpcListRow> rows() {
    return rows;
  }

  List<String> notes() {
    return notes;
  }
}
