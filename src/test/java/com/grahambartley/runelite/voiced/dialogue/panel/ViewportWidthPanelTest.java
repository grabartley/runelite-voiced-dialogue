package com.grahambartley.runelite.voiced.dialogue.panel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import org.junit.Test;

public class ViewportWidthPanelTest {

  private final ViewportWidthPanel panel = new ViewportWidthPanel(new BorderLayout());

  @Test
  public void tracksTheViewportWidthButScrollsVertically() {
    assertTrue(panel.getScrollableTracksViewportWidth());
    assertFalse(panel.getScrollableTracksViewportHeight());
  }

  @Test
  public void keepsItsLayout() {
    assertTrue(panel.getLayout() instanceof BorderLayout);
  }

  @Test
  public void theViewportSizeIsItsPreferredSize() {
    panel.add(new JLabel("Guard"));
    Dimension preferred = panel.getPreferredSize();

    assertEquals(preferred, panel.getPreferredScrollableViewportSize());
  }

  @Test
  public void scrollsAFixedUnitAndAPageAtATime() {
    Rectangle visible = new Rectangle(0, 0, 200, 300);

    assertEquals(
        ViewportWidthPanel.UNIT_INCREMENT,
        panel.getScrollableUnitIncrement(visible, SwingConstants.VERTICAL, 1));
    assertEquals(300, panel.getScrollableBlockIncrement(visible, SwingConstants.VERTICAL, 1));
    assertEquals(200, panel.getScrollableBlockIncrement(visible, SwingConstants.HORIZONTAL, 1));
  }
}
