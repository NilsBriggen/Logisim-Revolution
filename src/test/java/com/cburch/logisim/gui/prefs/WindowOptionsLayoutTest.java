/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.prefs;

import static com.cburch.logisim.gui.generic.FormLayoutTestSupport.atScale;
import static com.cburch.logisim.gui.generic.FormLayoutTestSupport.layoutTree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.Strings;
import com.cburch.logisim.gui.generic.ScrollableForm;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.Container;
import java.awt.Point;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WindowOptionsLayoutTest {
  @Test
  void explicitHelpAndSliderFontsFollowLiveDoubleToSingleScale() throws Exception {
    atScale(2.0, () -> {
      final var options = new WindowOptions(null);
      final var help = findChild(options, JTextArea.class);
      final var slider = findChild(options, JSlider.class);
      assertNotNull(help);
      assertNotNull(slider);
      assertEquals(Strings.S.get("windowScaleHelp"), help.getText());
      final var largeSize = help.getFont().getSize2D();
      assertEquals(UiFonts.body().getSize2D(), largeSize);

      UiScale.setFactor(1.0);
      SwingUtilities.updateComponentTreeUI(options);

      assertTrue(help.getFont().isItalic());
      assertTrue(help.getFont().getSize2D() < largeSize);
      assertEquals(UiFonts.body().getSize2D(), help.getFont().getSize2D());
      var labels = 0;
      for (final var values = slider.getLabelTable().elements(); values.hasMoreElements(); ) {
        final var label = (JLabel) values.nextElement();
        assertEquals(UiFonts.body().getSize2D(), label.getFont().getSize2D());
        labels++;
      }
      assertEquals(3, labels);
      assertEquals(Strings.S.get("windowScaleHelp"), help.getText());
    });
  }

  private static <T> T findChild(Container parent, Class<T> type) {
    for (final var child : parent.getComponents()) {
      if (type.isInstance(child)) return type.cast(child);
      if (child instanceof Container nested) {
        final var found = findChild(nested, type);
        if (found != null) return found;
      }
    }
    return null;
  }

  @ParameterizedTest
  @ValueSource(doubles = {1.0, 1.6, 2.0})
  void captionsAndActionsFitFixedNarrowViewport(double scale) throws Exception {
    atScale(scale, () -> {
      final var options = new WindowOptions(null);
      options.localeChanged();
      final var form = new ScrollableForm(options);
      form.setHelpText(options.getHelpText());
      final var viewport = new JViewport();
      viewport.setView(form);
      // 760 physical pixels at 2.0: slightly less than the actual form area in a 1200px frame.
      // Never enlarge this to the form's minimum; that hid the native overflow in older tests.
      final var width = UiScale.scaled(380);
      viewport.setSize(width, UiScale.scaled(400));
      for (var pass = 0; pass < 3; pass++) layoutTree(viewport);
      assertTrue(form.getScrollableTracksViewportWidth());
      assertEquals(width, form.getWidth());
      assertEquals(2, assertContentsFit(options, form, width));

      final var narrowHeight = form.getPreferredSize().height;
      viewport.setSize(UiScale.scaled(700), UiScale.scaled(400));
      for (var pass = 0; pass < 3; pass++) layoutTree(viewport);
      assertTrue(narrowHeight > form.getPreferredSize().height);
    });
  }

  private static int assertContentsFit(Container parent, Container form, int width) {
    var buttons = 0;
    for (final var child : parent.getComponents()) {
      if (!child.isVisible()) continue;
      final var position = SwingUtilities.convertPoint(child, new Point(0, 0), form);
      assertTrue(position.x >= 0);
      assertTrue(position.x + child.getWidth() <= width, child.getClass().getSimpleName());
      if (child instanceof JButton button) {
        buttons++;
        assertTrue(button.getWidth() >= button.getPreferredSize().width, button.getText());
      } else if (child instanceof JLabel label) {
        assertTrue(label.getWidth() >= label.getPreferredSize().width, label.getText());
      } else if (child instanceof JTextArea text) {
        assertTrue(text.getLineWrap());
        assertTrue(text.getHeight() >= text.getPreferredSize().height);
      }
      if (child instanceof JPanel nested) buttons += assertContentsFit(nested, form, width);
    }
    return buttons;
  }
}
