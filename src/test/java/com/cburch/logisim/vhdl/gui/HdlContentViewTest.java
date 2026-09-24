/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.vhdl.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import javax.swing.SwingUtilities;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.junit.jupiter.api.Test;

class HdlContentViewTest {
  @Test
  void syntaxThemeReloadPreservesScaledCodeAndGutterWithoutEditingText() throws Exception {
    final var holder = new HdlContentView[1];
    final var originalZoom = UIScale.getZoomFactor();
    SwingUtilities.invokeAndWait(() -> {
      FlatLightLaf.setup();
      holder[0] = new HdlContentView(null);
      holder[0].setText("entity example is\nend example;\n");
    });
    final var scroll = (RTextScrollPane) holder[0].getComponent(0);
    final var editor = (RSyntaxTextArea) scroll.getTextArea();
    try {
      for (final var scale : new double[] {1.0, 1.6, 2.0, 1.0}) {
        for (final var theme : new String[] {"dark", "default"}) {
          SwingUtilities.invokeAndWait(() -> {
            UiScale.setFactor(scale);
            try (final var input = RSyntaxTextArea.class.getResourceAsStream(
                "/org/fife/ui/rsyntaxtextarea/themes/" + theme + ".xml")) {
              assertNotNull(input);
              org.fife.ui.rsyntaxtextarea.Theme.load(input).apply(editor);
            } catch (java.io.IOException e) {
              throw new AssertionError(e);
            }
          });
          // Allow the view's coalesced font refresh to follow the syntax-theme reload.
          SwingUtilities.invokeAndWait(() -> {
            assertEquals(UiFonts.mono(), editor.getFont());
            assertEquals(editor.getFont(), scroll.getGutter().getLineNumberFont());
            for (final var style : editor.getSyntaxScheme().getStyles()) {
              if (style != null && style.font != null) {
                assertEquals(editor.getFont().getSize(), style.font.getSize());
              }
            }
            assertEquals("entity example is\nend example;\n", editor.getText());
          });
        }
      }
    } finally {
      SwingUtilities.invokeAndWait(() -> UIScale.setZoomFactor(originalZoom));
    }
  }
}
