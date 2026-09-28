/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;

class AttrTableElisionTest {

  private static JTable tableWithColumnWidth(int width, String value) {
    final var table =
        new JTable(new DefaultTableModel(new Object[][] {{value}}, new Object[] {"Value"}));
    table.setRowHeight(20);
    table.getColumnModel().getColumn(0).setWidth(width);
    table.setSize(width, 20);
    return table;
  }

  @Test
  void offersTheFullTextOnlyWhenTheColumnCutsItOff() {
    final var text = "a value that is far too long for a narrow column";

    assertEquals(text, AttrTable.elidedCellText(tableWithColumnWidth(30, text), new Point(5, 5)));
    assertNull(AttrTable.elidedCellText(tableWithColumnWidth(2000, text), new Point(5, 5)));
  }

  private static JTable twoColumnTable(int width, String label, String value) {
    final var table =
        new JTable(
            new DefaultTableModel(new Object[][] {{label, value}}, new Object[] {"Name", "Value"}));
    table.setRowHeight(20);
    table.setSize(width, 20);
    AttrTable.fitLabelColumn(table);
    table.doLayout();
    return table;
  }

  @Test
  void theNameColumnGrowsToFitLongNamesWithinLimits() {
    final var name = "Data bus implementation";
    final var probe = twoColumnTable(1000, name, "x");
    final var nameWidth =
        probe.prepareRenderer(probe.getCellRenderer(0, 0), 0, 0).getPreferredSize().width;
    // A width at which an even split cuts the name off but the name column's limit does not.
    final var total = (int) (nameWidth * 1.8);
    final var table = twoColumnTable(total, name, "One bidirectional");
    final var labelWidth = table.getColumnModel().getColumn(0).getWidth();
    assertTrue(labelWidth > total / 2, "the name column must grow past half: " + labelWidth);
    assertTrue(labelWidth <= total * AttrTable.MAX_LABEL_SHARE + 1, "values keep their share");
    assertNull(AttrTable.elidedCellText(table, new Point(5, 5)), "the name is no longer cut off");

    final var narrow = twoColumnTable(400, "Width", "8");
    assertEquals(
        (int) (400 * AttrTable.MIN_LABEL_SHARE),
        narrow.getColumnModel().getColumn(0).getWidth(),
        1.0);
  }

  @Test
  void pickersAreTitledWithTheirAttribute() {
    assertEquals("Label font", AttrTable.pickerTitle("Label font"));
    assertEquals("Color", AttrTable.pickerTitle("Color: "));
    assertFalse(AttrTable.pickerTitle(null).isBlank(), "a nameless picker keeps a generic title");
  }

  @Test
  void offersNothingOutsideTheCellsOrForEmptyCells() {
    assertNull(AttrTable.elidedCellText(tableWithColumnWidth(30, "x"), new Point(5, 500)));
    assertNull(AttrTable.elidedCellText(tableWithColumnWidth(30, ""), new Point(5, 5)));
  }
}
