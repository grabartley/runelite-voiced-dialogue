package com.grahambartley.runelite.voiced.dialogue.panel;

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JTextArea;
import net.runelite.client.ui.FontManager;

final class WrappingLabel extends JTextArea {

  WrappingLabel(String text, Color color) {
    super(text);
    setLineWrap(true);
    setWrapStyleWord(true);
    setEditable(false);
    setFocusable(false);
    setOpaque(false);
    setBorder(null);
    setFont(FontManager.getRunescapeSmallFont());
    setForeground(color);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(1, super.getPreferredSize().height);
  }
}
