/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.prefs;

import static com.cburch.logisim.gui.generic.FormLayoutTestSupport.atScale;
import static com.cburch.logisim.gui.generic.FormLayoutTestSupport.layoutTree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.generic.ScrollableForm;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.Container;
import java.awt.Point;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SoftwaresOptionsLayoutTest {
  @Test
  void completeValidationCaptionFitsActualNarrowViewportAtDoubleScale() throws Exception {
    atScale(
        2.0,
        () -> {
          final var software = new SoftwaresOptions(null);
          software.localeChanged();
          final var caption = findCaption(software);
          assertNotNull(caption);
          // Exact English caption from native QA; do not change the user's locale/preferences.
          caption.setText("Use Questa Advanced Simulator to validate HDL entities");
          final var largeFontSize = caption.getFont().getSize2D();
          final var form = new ScrollableForm(software);
          form.setHelpText(software.getHelpText());
          final var viewport = new JViewport();
          viewport.setView(form);

          // 792px is the observed form viewport in the 1200px/2.0 Preferences window.
          // Also cover widening, returning to that width, and the tighter existing fixture.
          for (final var width : new int[] {792, 1200, 792, 760}) {
            viewport.setSize(width, 780);
            for (var pass = 0; pass < 3; pass++) layoutTree(viewport);
            assertTrue(form.getScrollableTracksViewportWidth());
            assertEquals(width, form.getWidth());
            assertCaptionEndVisible(caption, width < 1000);
          }

          UiScale.setFactor(1.0);
          SwingUtilities.updateComponentTreeUI(form);
          for (var pass = 0; pass < 3; pass++) layoutTree(viewport);
          assertTrue(caption.getFont().getSize2D() < largeFontSize);
          assertEquals(UiFonts.body().getSize2D(), caption.getFont().getSize2D());
          assertCaptionEndVisible(caption, false);
        });
  }

  private static JTextArea findCaption(Container parent) {
    for (final var child : parent.getComponents()) {
      if (child instanceof JTextArea area) return area;
      if (child instanceof Container nested) {
        final var found = findCaption(nested);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static void assertCaptionEndVisible(JTextArea caption, boolean wraps) {
    try {
      final var first = caption.modelToView2D(0);
      final var last = caption.modelToView2D(caption.getDocument().getLength() - 1);
      assertNotNull(first);
      assertNotNull(last);
      if (wraps) {
        assertTrue(last.getY() > first.getY(), "Fixture must exercise a wrapped last line");
      }
      assertTrue(
          last.getMaxY() <= caption.getHeight() - caption.getInsets().bottom,
          "Final word 'entities' must fit inside the actual caption height");
      assertTrue(last.getMaxX() <= caption.getWidth() - caption.getInsets().right);
      assertTrue(caption.getHeight() >= caption.getPreferredSize().height);
    } catch (BadLocationException exception) {
      throw new AssertionError(exception);
    }
  }

  @ParameterizedTest
  @ValueSource(doubles = {1.0, 1.6, 2.0})
  void allFiveBrowseActionsRemainVisibleWhenFieldsShrink(double scale) throws Exception {
    atScale(
        scale,
        () -> {
          final var software = new SoftwaresOptions(null);
          software.localeChanged();
          final var form = new ScrollableForm(software);
          final var viewport = new JViewport();
          viewport.setView(form);
          final var width = UiScale.scaled(380);
          assertTrue(width < software.getPreferredSize().width);
          viewport.setSize(width, UiScale.scaled(600));
          for (var pass = 0; pass < 3; pass++) layoutTree(viewport);

          assertTrue(form.getScrollableTracksViewportWidth());
          assertEquals(width, form.getWidth());
          var buttons = 0;
          for (final var component : software.getComponents()) {
            if (component instanceof JButton button) {
              buttons++;
              final var position = SwingUtilities.convertPoint(button, new Point(0, 0), form);
              assertTrue(position.x >= 0);
              assertTrue(position.x + button.getWidth() <= viewport.getWidth());
              assertEquals(button.getPreferredSize().width, button.getWidth());
            }
          }
          assertEquals(5, buttons);
        });
  }
}
