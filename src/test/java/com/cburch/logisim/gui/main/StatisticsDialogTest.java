/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.generic.FormLayoutTestSupport;
import com.cburch.logisim.util.UiScale;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;

class StatisticsDialogTest {
  @Test
  void headersTotalsAndLargeCountsAreMeasuredAtFinalFontSize() throws Exception {
    FormLayoutTestSupport.atScale(2, () -> {
      final var table = table();
      final var metrics = table.getFontMetrics(table.getFont());
      assertEquals(JTable.AUTO_RESIZE_OFF, table.getAutoResizeMode());
      for (var i = 0; i < table.getColumnCount(); i++) {
        final var width = table.getColumnModel().getColumn(i).getPreferredWidth();
        assertTrue(width > metrics.stringWidth(table.getColumnName(i)));
        assertTrue(width > metrics.stringWidth(table.getValueAt(0, i).toString()));
      }
      assertTrue(table.getRowHeight() > metrics.getHeight());
      // Narrow windows scroll instead of squeezing important count headings into ellipses.
      final var measured = table.getColumnModel().getTotalColumnWidth();
      table.setBounds(0, 0, 400, 200);
      assertEquals(measured, table.getColumnModel().getTotalColumnWidth());
    });
  }

  @Test
  void liveScaleDownRemeasuresRowsAndColumns() throws Exception {
    FormLayoutTestSupport.atScale(2, () -> {
      final var table = table();
      final var largeRow = table.getRowHeight();
      final var largeWidth = table.getColumnModel().getColumn(0).getPreferredWidth();
      UiScale.setFactor(1);
      SwingUtilities.updateComponentTreeUI(table);
      assertTrue(table.getRowHeight() < largeRow);
      assertTrue(table.getColumnModel().getColumn(0).getPreferredWidth() < largeWidth);
    });
  }

  private static StatisticsDialog.StatisticsTable table() {
    final var table = new StatisticsDialog.StatisticsTable();
    table.setModel(new DefaultTableModel(
        new Object[][] {{"Total including subcircuits", "Built-in library", 123456789}},
        new String[] {"Component", "Library", "Recursive count"}));
    table.updateMetrics();
    return table;
  }
}
