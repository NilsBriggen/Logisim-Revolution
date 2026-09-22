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
import static org.junit.jupiter.api.Assertions.assertNull;

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

  @Test
  void offersNothingOutsideTheCellsOrForEmptyCells() {
    assertNull(AttrTable.elidedCellText(tableWithColumnWidth(30, "x"), new Point(5, 500)));
    assertNull(AttrTable.elidedCellText(tableWithColumnWidth(30, ""), new Point(5, 5)));
  }
}
