package com.grahambartley.runelite.voiced.dialogue.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

final class NpcListRow extends JPanel {

  static final int CHATHEAD_SIZE = 32;

  private final JLabel chathead = new JLabel();
  private final JLabel name = new JLabel();
  private final JLabel detail = new JLabel();
  private final JLabel badge = new JLabel();

  NpcListRow(NpcListEntry entry, ChatheadImages chatheads, Consumer<NpcListEntry> onOpen) {
    super(new BorderLayout(8, 0));
    setBackground(ColorScheme.DARKER_GRAY_COLOR);
    setBorder(new EmptyBorder(6, 6, 6, 8));
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    setToolTipText("Edit how " + entry.name() + " sounds");

    chatheads.load(entry.name(), CHATHEAD_SIZE, chathead::setIcon);
    add(chathead, BorderLayout.WEST);

    JPanel text = new JPanel(new GridLayout(2, 1));
    text.setOpaque(false);
    name.setText(entry.name());
    name.setFont(FontManager.getRunescapeBoldFont());
    name.setForeground(Color.WHITE);
    detail.setText(detailText(entry));
    detail.setFont(FontManager.getRunescapeSmallFont());
    detail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    text.add(name);
    text.add(detail);
    add(text, BorderLayout.CENTER);

    badge.setFont(FontManager.getRunescapeSmallFont());
    badge.setForeground(ColorScheme.BRAND_ORANGE);
    badge.setText(entry.edited() ? "Edited" : "");
    add(badge, BorderLayout.EAST);

    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent e) {
            setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
          }

          @Override
          public void mouseExited(MouseEvent e) {
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            onOpen.accept(entry);
          }
        });
  }

  static String detailText(NpcListEntry entry) {
    String npcs = NpcDetailView.npcCount(entry.ids().size());
    return entry.heard() ? npcs + " · heard" : npcs;
  }

  String nameText() {
    return name.getText();
  }

  String detailLabelText() {
    return detail.getText();
  }

  String badgeText() {
    return badge.getText();
  }

  JLabel chatheadLabel() {
    return chathead;
  }
}
