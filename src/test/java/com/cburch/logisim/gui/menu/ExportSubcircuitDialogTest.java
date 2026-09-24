/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.generic.FormLayoutTestSupport;
import com.cburch.logisim.util.UiScale;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;

class ExportSubcircuitDialogTest {
  @Test
  void scaledRowsAndHeadersFitAndShrinkOnLiveScaleDown() throws Exception {
    FormLayoutTestSupport.atScale(2, () -> {
      final var table = new ExportSubcircuitDialog.DependencyTable(new DefaultTableModel(
          new Object[][] {{"dependency", "main_dependency"}},
          new String[] {"Original Circuit Name", "Exported Circuit Name"}));
      assertMetrics(table);
      assertFalse(table.getTableHeader().getReorderingAllowed());
      final var largeRow = table.getRowHeight();
      final var largeWidth = table.getColumnModel().getColumn(0).getMinWidth();
      UiScale.setFactor(1);
      SwingUtilities.updateComponentTreeUI(table);
      assertMetrics(table);
      assertTrue(table.getRowHeight() < largeRow);
      assertTrue(table.getColumnModel().getColumn(0).getMinWidth() < largeWidth);
    });
  }

  @Test
  void emptyDependenciesStillHaveAVisibleTableArea() throws Exception {
    FormLayoutTestSupport.atScale(2, () -> {
      final var table = new ExportSubcircuitDialog.DependencyTable(new DefaultTableModel(
          new String[] {"Original", "Exported"}, 0));
      assertTrue(table.getPreferredScrollableViewportSize().height >= 2 * table.getRowHeight());
    });
  }

  private static void assertMetrics(ExportSubcircuitDialog.DependencyTable table) {
    final var metrics = table.getFontMetrics(table.getFont());
    assertTrue(table.getRowHeight() >= metrics.getHeight() + UiScale.scaled(8));
    for (var i = 0; i < table.getColumnCount(); i++) {
      assertTrue(table.getColumnModel().getColumn(i).getMinWidth()
          > metrics.stringWidth(table.getColumnName(i)));
    }
  }
}
