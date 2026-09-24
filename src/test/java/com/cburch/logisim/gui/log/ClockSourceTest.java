/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.util.UiScale;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Dimension;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class ClockSourceTest {
  @Test
  void measuredDialogIsBoundedToTheAvailableWorkArea() {
    assertEquals(new Dimension(840, 720),
        ClockSource.boundedSize(new Dimension(840, 720), new Dimension(2560, 1600)));
    assertEquals(new Dimension(640, 480),
        ClockSource.boundedSize(new Dimension(840, 720), new Dimension(640, 480)));
  }

  @Test
  void longExplanationKeepsSelectableRowsAtDoubleScale() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var oldLaf = UIManager.getLookAndFeel();
      final var oldZoom = UIScale.getZoomFactor();
      final var labelOverride = UIManager.put("Label.font", null);
      try {
        FlatLightLaf.setup();
        UiScale.setFactor(2);
        final var selector = new JTable(12, 1);
        selector.setRowHeight(UiScale.scaled(24));
        final var content = new ClockSource.ChooserContent(selector, selector.getRowHeight());
        final var explanation = (JScrollPane) content.getComponent(0);
        final var text = (JTextArea) explanation.getViewport().getView();
        text.setText("The circuit contains multiple clocks. Select a clock or signal. ".repeat(20));
        content.measure(800);
        content.setSize(800, 400);
        content.doLayout();
        final var signals = (JScrollPane) content.getComponent(1);
        explanation.doLayout();
        explanation.getViewport().doLayout();
        signals.doLayout();
        signals.getViewport().doLayout();
        assertTrue(signals.getViewport().getExtentSize().height >= 3 * selector.getRowHeight(),
            "three complete rows must fit inside the viewport, excluding scroll-pane chrome");
        assertTrue(explanation.getViewport().getExtentSize().height < text.getPreferredSize().height,
            "long descriptions scroll rather than consuming the signal list");
        assertTrue(signals.getY() + signals.getHeight() <= content.getHeight());
      } finally {
        try {
          UIManager.setLookAndFeel(oldLaf);
        } catch (javax.swing.UnsupportedLookAndFeelException e) {
          throw new AssertionError(e);
        }
        UIScale.setZoomFactor(oldZoom);
        UIManager.put("Label.font", labelOverride);
      }
    });
  }
}
