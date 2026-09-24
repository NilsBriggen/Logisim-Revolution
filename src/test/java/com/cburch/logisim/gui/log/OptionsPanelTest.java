/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.util.UiScale;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class OptionsPanelTest {
  @Test
  void sectionsStackAtConstrainedWidthsInsteadOfClippingLabels() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var oldLaf = UIManager.getLookAndFeel();
      final var oldZoom = UIScale.getZoomFactor();
      final var override = UIManager.put("Label.font", null);
      try {
        FlatLightLaf.setup();
        for (final var scale : new double[] {1, 1.6, 2}) {
          UiScale.setFactor(scale);
          final var frame = mock(LogFrame.class);
          final var model = mock(Model.class);
          when(frame.getModel()).thenReturn(model);
          when(frame.makeSelectionButton()).thenReturn(new JButton("Add or Remove Signals"));
          when(model.isStepMode()).thenReturn(true);
          when(model.getTimeScale()).thenReturn(5000L);
          when(model.getGateDelay()).thenReturn(200L);
          when(model.getHistoryLimit()).thenReturn(400);
          final var panel = new OptionsPanel(frame);
          for (final var width : new int[] {680, 1100}) {
            panel.setSize(width, 300);
            layout(panel);
            final var view = (OptionsPanel.ScrollablePanel) panel.pane.getViewport().getView();
            final var size = view.getPreferredSize();
            view.setSize(size);
            layout(view);
            assertTrue(panel.selectionButton.getWidth() >= panel.selectionButton.getPreferredSize().width);
            assertTrue(panel.stepTime.getWidth() >= panel.stepTime.getPreferredSize().width);
            assertTrue(panel.realTime.getWidth() >= panel.realTime.getPreferredSize().width);
            assertTrue(panel.limitLabel.getWidth() >= panel.limitLabel.getPreferredSize().width);
            if (scale == 2 && width == 1100) {
              assertTrue(panel.historyPanel.getY() > panel.optionsPanel.getY(),
                  "2x sections must stack inside the real ~1100px drawer viewport");
            }
            assertTrue(size.height > 0);
          }
        }
      } finally {
        try {
          UIManager.setLookAndFeel(oldLaf);
        } catch (javax.swing.UnsupportedLookAndFeelException e) {
          throw new AssertionError(e);
        }
        UIScale.setZoomFactor(oldZoom);
        UIManager.put("Label.font", override);
      }
    });
  }

  private static void layout(Container container) {
    container.doLayout();
    for (final var child : container.getComponents()) {
      if (child instanceof Container nested) layout(nested);
    }
  }
}
