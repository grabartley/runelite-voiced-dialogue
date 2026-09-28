package com.grahambartley.runelite.voiced.dialogue.panel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceImportPlan;
import javax.swing.JOptionPane;
import org.junit.Test;

public class SwingTransferDialogsTest {

  @Test
  public void theFirstButtonMerges() {
    assertEquals(NpcVoiceImportPlan.Mode.MERGE, SwingTransferDialogs.importMode(0));
  }

  @Test
  public void theSecondButtonReplacesAll() {
    assertEquals(NpcVoiceImportPlan.Mode.REPLACE_ALL, SwingTransferDialogs.importMode(1));
  }

  @Test
  public void cancelOrClosingTheDialogImportsNothing() {
    assertNull(SwingTransferDialogs.importMode(2));
    assertNull(SwingTransferDialogs.importMode(JOptionPane.CLOSED_OPTION));
  }
}
