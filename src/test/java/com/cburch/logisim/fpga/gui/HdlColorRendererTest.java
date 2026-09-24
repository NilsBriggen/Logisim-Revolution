/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class HdlColorRendererTest {
  @Test
  void statusUsesReadableTablePairAndNamedIconWithoutLeakingIntoOtherRows() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var table = new JTable(2, 2);
      final var renderer = new HdlColorRenderer();
      for (final var status : new String[] {HdlColorRenderer.SUPPORT_STRING,
          HdlColorRenderer.NO_SUPPORT_STRING, HdlColorRenderer.UNKNOWN_STRING}) {
        renderer.getTableCellRendererComponent(table, status, false, false, 0, 1);
        assertEquals(UIManager.getColor("Table.background"), renderer.getBackground());
        assertEquals(UIManager.getColor("Table.foreground"), renderer.getForeground());
        assertEquals(table.getFont(), renderer.getFont());
        assertNotNull(renderer.getIcon());
        assertTrue(!renderer.getText().isBlank());
        assertTrue(renderer.getInsets().left > 0);
      }
      renderer.getTableCellRendererComponent(table, "Facing", false, false, 1, 0);
      assertEquals("Facing", renderer.getText());
      assertNull(renderer.getIcon());
    });
  }
}
