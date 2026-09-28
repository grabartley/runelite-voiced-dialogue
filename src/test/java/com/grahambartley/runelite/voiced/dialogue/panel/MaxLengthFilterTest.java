package com.grahambartley.runelite.voiced.dialogue.panel;

import static org.junit.Assert.assertEquals;

import javax.swing.JTextField;
import javax.swing.text.AbstractDocument;
import org.junit.Test;

public class MaxLengthFilterTest {

  private static JTextField limited(int max) {
    JTextField field = new JTextField();
    ((AbstractDocument) field.getDocument()).setDocumentFilter(new MaxLengthFilter(max));
    return field;
  }

  @Test
  public void textWithinTheLimitIsKept() {
    JTextField field = limited(5);
    field.setText("abc");
    assertEquals("abc", field.getText());
  }

  @Test
  public void textPastTheLimitIsCut() {
    JTextField field = limited(5);
    field.setText("abcdefgh");
    assertEquals("abcde", field.getText());
  }

  @Test
  public void aFullFieldTakesNoMore() throws Exception {
    JTextField field = limited(3);
    field.setText("abc");
    field.getDocument().insertString(1, "zz", null);
    assertEquals("abc", field.getText());
  }

  @Test
  public void replacingASelectionFreesItsRoom() throws Exception {
    JTextField field = limited(4);
    field.setText("abcd");
    ((AbstractDocument) field.getDocument()).replace(1, 2, "xyz", null);
    assertEquals("axyd", field.getText());
  }

  @Test
  public void lineBreaksBecomeSpaces() {
    JTextField field = limited(20);
    field.setText("one\ntwo\rthree");
    assertEquals("one two three", field.getText());
  }
}
