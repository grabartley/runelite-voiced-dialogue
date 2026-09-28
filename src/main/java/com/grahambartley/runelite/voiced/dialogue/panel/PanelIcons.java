package com.grahambartley.runelite.voiced.dialogue.panel;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.ColorScheme;

final class PanelIcons {

  static final int NAVIGATION_SIZE = 16;

  private PanelIcons() {}

  static BufferedImage navigation() {
    BufferedImage image =
        new BufferedImage(NAVIGATION_SIZE, NAVIGATION_SIZE, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = smooth(image);
    try {
      g.setColor(ColorScheme.BRAND_ORANGE);
      g.fill(new RoundRectangle2D.Double(1, 2, 14, 9, 5, 5));
      Path2D tail = new Path2D.Double();
      tail.moveTo(4, 10);
      tail.lineTo(4, 14);
      tail.lineTo(8, 10);
      tail.closePath();
      g.fill(tail);
      g.setColor(Color.WHITE);
      g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(5, 5, 5, 8);
      g.drawLine(8, 4, 8, 9);
      g.drawLine(11, 5, 11, 8);
    } finally {
      g.dispose();
    }
    return image;
  }

  static BufferedImage chatheadPlaceholder(int size) {
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = smooth(image);
    try {
      g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
      double head = size * 0.42;
      g.fill(new Ellipse2D.Double((size - head) / 2, size * 0.14, head, head));
      double body = size * 0.72;
      g.fill(new Ellipse2D.Double((size - body) / 2, size * 0.58, body, size * 0.6));
    } finally {
      g.dispose();
    }
    return image;
  }

  private static Graphics2D smooth(BufferedImage image) {
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    return g;
  }
}
