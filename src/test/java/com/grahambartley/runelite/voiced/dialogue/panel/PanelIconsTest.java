package com.grahambartley.runelite.voiced.dialogue.panel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import org.junit.Test;

public class PanelIconsTest {

  private static boolean hasVisiblePixel(BufferedImage image) {
    for (int x = 0; x < image.getWidth(); x++) {
      for (int y = 0; y < image.getHeight(); y++) {
        if ((image.getRGB(x, y) >>> 24) != 0) {
          return true;
        }
      }
    }
    return false;
  }

  @Test
  public void theNavigationIconIsSixteenSquareAndDrawn() {
    BufferedImage icon = PanelIcons.navigation();

    assertEquals(PanelIcons.NAVIGATION_SIZE, icon.getWidth());
    assertEquals(PanelIcons.NAVIGATION_SIZE, icon.getHeight());
    assertTrue(hasVisiblePixel(icon));
  }

  @Test
  public void theNavigationIconFillsItsSquareSoRuneLiteNeverStretchesIt() {
    BufferedImage icon = PanelIcons.navigation();

    assertEquals(icon.getWidth(), icon.getHeight());
    assertTrue((icon.getRGB(0, PanelIcons.NAVIGATION_SIZE / 2) >>> 24) > 0);
    assertTrue(
        (icon.getRGB(PanelIcons.NAVIGATION_SIZE - 1, PanelIcons.NAVIGATION_SIZE / 2) >>> 24) > 0);
  }

  @Test
  public void thePlaceholderIsTheRequestedSizeAndDrawn() {
    BufferedImage placeholder = PanelIcons.chatheadPlaceholder(48);

    assertEquals(48, placeholder.getWidth());
    assertEquals(48, placeholder.getHeight());
    assertTrue(hasVisiblePixel(placeholder));
  }

  @Test
  public void thePlaceholderCornersAreTransparent() {
    BufferedImage placeholder = PanelIcons.chatheadPlaceholder(32);

    assertEquals(0, placeholder.getRGB(0, 0) >>> 24);
  }
}
