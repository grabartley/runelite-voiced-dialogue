package com.grahambartley.runelite.voiced.dialogue.panel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

public class PlaceholderTextAreaTest {

  private static BufferedImage paint(PlaceholderTextArea area) {
    area.setSize(200, 60);
    BufferedImage image = new BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    area.paint(g);
    g.dispose();
    return image;
  }

  private static boolean samePixels(BufferedImage a, BufferedImage b) {
    for (int x = 0; x < a.getWidth(); x++) {
      for (int y = 0; y < a.getHeight(); y++) {
        if (a.getRGB(x, y) != b.getRGB(x, y)) {
          return false;
        }
      }
    }
    return true;
  }

  @Test
  public void wrapsWordsAndKeepsItsPlaceholder() {
    PlaceholderTextArea area = new PlaceholderTextArea("Plugin default", 3);

    assertTrue(area.getLineWrap());
    assertTrue(area.getWrapStyleWord());
    assertEquals(3, area.getRows());
    assertEquals("Plugin default", area.placeholder());
  }

  @Test
  public void anEmptyAreaDrawsThePlaceholder() {
    PlaceholderTextArea withPlaceholder = new PlaceholderTextArea("Plugin default", 3);
    PlaceholderTextArea withoutPlaceholder = new PlaceholderTextArea("", 3);

    assertFalse(samePixels(paint(withPlaceholder), paint(withoutPlaceholder)));
  }

  @Test
  public void typedTextHidesThePlaceholder() {
    PlaceholderTextArea first = new PlaceholderTextArea("Plugin default", 3);
    PlaceholderTextArea second = new PlaceholderTextArea("Something else entirely", 3);
    first.setText("Gruff");
    second.setText("Gruff");

    assertTrue(samePixels(paint(first), paint(second)));
  }

  @Test
  public void keepsAtLeastItsRowsTallAndNeverForcesTheFormWider() {
    PlaceholderTextArea area = new PlaceholderTextArea("Plugin default", 3);
    PlaceholderTextArea oneRow = new PlaceholderTextArea("Plugin default", 1);

    assertEquals(1, area.getPreferredSize().width);
    assertEquals(area.getPreferredSize(), area.getMinimumSize());
    assertTrue(area.getMinimumSize().height > oneRow.getMinimumSize().height);
  }
}
