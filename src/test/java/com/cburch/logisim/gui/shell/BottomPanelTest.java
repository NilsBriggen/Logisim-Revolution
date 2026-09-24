/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class BottomPanelTest {

  @Test
  void closingOnePanelKeepsOthersAndExistingActionsReopenTheSameContent() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var drawer = new BottomPanel();
      final var first = new JPanel();
      final var second = new JPanel();
      final var hidden = new AtomicInteger();
      drawer.setOnClose(hidden::incrementAndGet);
      drawer.addPanel("timing", "Timing", null, first);
      drawer.addPanel("test", "Test", null, second);
      final var close = (IntConsumer) drawer.getClientProperty(
          FlatClientProperties.TABBED_PANE_TAB_CLOSE_CALLBACK);
      close.accept(0);
      assertEquals(1, drawer.getTabCount());
      assertSame(second, drawer.getSelectedComponent());
      assertEquals(0, hidden.get());
      drawer.setTitle("timing", "Renamed timing");
      assertTrue(drawer.showPanel("timing"));
      assertSame(first, drawer.getSelectedComponent());
      assertEquals("Renamed timing", drawer.getTitleAt(drawer.getSelectedIndex()));
      close.accept(1);
      close.accept(0);
      assertEquals(1, hidden.get());
      assertTrue(drawer.showPanel("test"));
      assertSame(second, drawer.getSelectedComponent());
    });
  }

  @Test
  void minimumHeightLeavesFourResultRowsBelowControlsAtDifferentFontSizes() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      for (final var fontSize : new int[] {12, 19, 24}) {
        final var font = new Font(Font.DIALOG, Font.PLAIN, fontSize);
        final var table = new JTable(12, 3);
        table.setFont(font);
        table.setRowHeight(table.getFontMetrics(font).getHeight() + 6);
        final var status = new JLabel("2 passed / 2 failed");
        status.setFont(font);
        final var run = new JButton("Run");
        run.setFont(font);
        final var body = new JPanel(new BorderLayout());
        body.add(status, BorderLayout.NORTH);
        body.add(new JScrollPane(table));
        body.add(run, BorderLayout.SOUTH);
        final var nested = new JTabbedPane();
        nested.setFont(font);
        nested.addTab("Results", body);
        final var drawer = new BottomPanel();
        drawer.setFont(font);
        drawer.addPanel("test", "Test vector", null, nested);
        drawer.setSize(900, drawer.getMinimumSize().height);
        layoutTree(drawer);
        assertTrue(table.getParent().getHeight() >= 4 * table.getRowHeight(),
            "four rows must remain visible at font size " + fontSize);
        assertTrue(status.getHeight() >= status.getPreferredSize().height);
        assertTrue(run.getHeight() >= run.getPreferredSize().height);
      }
    });
  }

  private static void layoutTree(java.awt.Container container) {
    container.doLayout();
    for (final var child : container.getComponents()) {
      if (child instanceof java.awt.Container nested) layoutTree(nested);
    }
  }
}

