package com.grahambartley.runelite.voiced.dialogue.panel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import org.junit.Test;

public class WrappingLabelTest {

  private static final String LONG =
      "Blank fields keep the plugin's voice. Edited NPCs are re-voiced, and re-billed.";

  @Test
  public void readsAsAWrappedReadOnlyLabel() {
    WrappingLabel label = new WrappingLabel(LONG, Color.GRAY);

    assertEquals(LONG, label.getText());
    assertTrue(label.getLineWrap());
    assertTrue(label.getWrapStyleWord());
    assertFalse(label.isEditable());
    assertFalse(label.isFocusable());
    assertFalse(label.isOpaque());
    assertEquals(Color.GRAY, label.getForeground());
  }

  @Test
  public void neverAsksForMoreWidthThanItsParentGives() {
    assertEquals(1, new WrappingLabel(LONG, Color.GRAY).getPreferredSize().width);
  }

  @Test
  public void growsTallerWhenNarrowed() {
    WrappingLabel label = new WrappingLabel(LONG, Color.GRAY);
    label.setSize(1000, 10);
    int wide = label.getPreferredSize().height;

    label.setSize(80, 10);
    int narrow = label.getPreferredSize().height;

    assertTrue(narrow > wide);
  }
}
