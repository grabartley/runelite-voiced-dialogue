package com.grahambartley.runelite.voiced.dialogue.panel;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.JTextArea;

final class PlaceholderTextArea extends JTextArea {

  private final String placeholder;

  PlaceholderTextArea(String placeholder, int rows) {
    super(rows, 0);
    this.placeholder = placeholder;
    setLineWrap(true);
    setWrapStyleWord(true);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(1, super.getPreferredSize().height);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  String placeholder() {
    return placeholder;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (!getText().isEmpty()) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setColor(getDisabledTextColor());
      g2.setFont(getFont());
      Insets insets = getInsets();
      g2.drawString(placeholder, insets.left, insets.top + g2.getFontMetrics().getAscent());
    } finally {
      g2.dispose();
    }
  }
}
