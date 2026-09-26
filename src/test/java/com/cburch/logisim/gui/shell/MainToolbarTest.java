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

import com.cburch.draw.toolbar.Toolbar;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MainToolbarTest {

  @Test
  void simulationControlsStayOnTheRightWhenTheDrawingToolsAreHidden() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var tools = new Toolbar(null);
      tools.add(sized(new JButton("tool")), BorderLayout.LINE_START);
      final var simulation = new Toolbar(null);
      final var run = new JButton("run");
      run.setPreferredSize(new Dimension(40, 20));
      simulation.add(run, BorderLayout.LINE_START);
      final var row = new MainToolbar(tools, simulation);
      row.setSize(1200, row.getPreferredSize().height);
      layoutTree(row);
      final var shownRight = rightEdge(run, row);
      final var shownHeight = row.getPreferredSize().height;

      tools.setVisible(false);
      row.setSize(1200, row.getPreferredSize().height);
      layoutTree(row);

      assertEquals(shownRight, rightEdge(run, row));
      assertEquals(row.getWidth() - row.getInsets().right, rightEdge(simulation, row));
      assertEquals(shownHeight, row.getPreferredSize().height, "the row must not change height");
    });
  }

  private static JButton sized(JButton button) {
    button.setPreferredSize(new Dimension(40, 40));
    return button;
  }

  private static int rightEdge(Component component, MainToolbar row) {
    return SwingUtilities.convertPoint(component, component.getWidth(), 0, row).x;
  }

  private static void layoutTree(Container container) {
    container.doLayout();
    for (final var child : container.getComponents()) {
      if (child instanceof Container nested) layoutTree(nested);
    }
  }
}
