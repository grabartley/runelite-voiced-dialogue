package com.grahambartley.runelite.voiced.dialogue.panel;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

final class PanelIcons {

  static final int NAVIGATION_SIZE = 16;

  static final String NAVIGATION_RESOURCE = "navigation_icon.png";

  private PanelIcons() {}

  static BufferedImage navigation() {
    BufferedImage icon = ImageUtil.loadImageResource(PanelIcons.class, NAVIGATION_RESOURCE);
    return ChatheadImages.fit(icon, NAVIGATION_SIZE);
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
