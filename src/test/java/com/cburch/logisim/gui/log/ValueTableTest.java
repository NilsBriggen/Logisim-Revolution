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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseEvent;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class ValueTableTest {
  @Test
  void measuresBeforeFirstPaintAndUsesBodyCoordinatesAfterScrolling() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var oldLaf = UIManager.getLookAndFeel();
      final var oldZoom = UIScale.getZoomFactor();
      FlatLightLaf.setup();
      final var model = mock(ValueTable.Model.class);
      when(model.getColumnCount()).thenReturn(1);
      when(model.getColumnName(0)).thenReturn("Long status and action heading");
      when(model.getRowCount()).thenReturn(1_000);
      when(model.isButtonColumn(0)).thenReturn(true);
      doAnswer(invocation -> {
        final int start = invocation.getArgument(0);
        final int count = invocation.getArgument(1);
        final ValueTable.Cell[][] cells = invocation.getArgument(2);
        for (var row = 0; row < count; row++) {
          cells[row][0] = new ValueTable.Cell("Show", null, null, "row " + (start + row));
        }
        return null;
      }).when(model).getRowData(anyInt(), anyInt(), any(ValueTable.Cell[][].class));
      final var table = new ValueTable(model);
      final var scroll = (JScrollPane) table.getComponent(0);
      final var body = (javax.swing.JComponent) scroll.getViewport().getView();
      table.addNotify();
      try {
        for (final var scale : new double[] {1.0, 1.6, 2.0, 1.0}) {
          UiScale.setFactor(scale);
          Theme.fireChanged();
          final var rowHeight = body.getPreferredSize().height / model.getRowCount();
          assertTrue(rowHeight > table.getFontMetrics(UiFonts.mono()).getHeight());
          assertTrue(body.getPreferredSize().width >= table.getFontMetrics(UiFonts.bodyBold())
              .stringWidth(model.getColumnName(0)));
          body.setSize(body.getPreferredSize());
          scroll.getViewport().setExtentSize(new Dimension(body.getWidth(), rowHeight * 3));
          scroll.getViewport().setViewPosition(new Point(0, rowHeight * 400));
          table.refreshData(rowHeight * 400, rowHeight * 403);
          assertEquals(401, table.findRow(rowHeight * 401 + 1, body.getHeight()));
          final var event = new MouseEvent(body, MouseEvent.MOUSE_CLICKED, 0, 0,
              UiScale.scaled(10), rowHeight * 401 + 1, 0, 0, 1, false, MouseEvent.NOBUTTON);
          for (final var listener : body.getMouseListeners()) listener.mouseClicked(event);
        }
        verify(model, org.mockito.Mockito.times(4)).handleButtonClick(401, 0, 0);
      } finally {
        table.removeNotify();
        try {
          UIManager.setLookAndFeel(oldLaf);
        } catch (javax.swing.UnsupportedLookAndFeelException e) {
          throw new AssertionError(e);
        }
        UIScale.setZoomFactor(oldZoom);
      }
    });
  }
}
