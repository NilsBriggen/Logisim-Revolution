/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class TextLineNumberTest {
  @Test
  void gutterTracksEditorFontAndRecomputesWidthWithoutChangingDigitCount() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var editor = new JTextArea("first\nsecond\nthird");
      editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
      final var gutter = new TextLineNumber(editor);
      final var narrow = gutter.getPreferredSize().width;
      editor.setFont(editor.getFont().deriveFont(26f));
      assertEquals(editor.getFont(), gutter.getFont());
      assertTrue(gutter.getPreferredSize().width > narrow);
      final var insets = gutter.getInsets();
      assertTrue(gutter.getPreferredSize().width >= insets.left + insets.right
          + gutter.getFontMetrics(gutter.getFont()).stringWidth("000"));
      editor.setFont(editor.getFont().deriveFont(13f));
      assertEquals(narrow, gutter.getPreferredSize().width);
    });
  }
}
