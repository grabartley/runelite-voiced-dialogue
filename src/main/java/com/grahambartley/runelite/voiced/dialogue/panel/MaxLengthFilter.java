package com.grahambartley.runelite.voiced.dialogue.panel;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

final class MaxLengthFilter extends DocumentFilter {

  private final int maxLength;

  MaxLengthFilter(int maxLength) {
    this.maxLength = maxLength;
  }

  @Override
  public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
      throws BadLocationException {
    replace(fb, offset, 0, text, attrs);
  }

  @Override
  public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
      throws BadLocationException {
    String incoming = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
    int room = Math.max(0, maxLength - (fb.getDocument().getLength() - length));
    fb.replace(offset, length, incoming.substring(0, Math.min(incoming.length(), room)), attrs);
  }
}
