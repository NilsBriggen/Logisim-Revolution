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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class PanelHeaderTest {

  @Test
  void actionsThatDoNotFitMoveBehindAMoreButtonAndComeBackWhenThereIsRoom() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var header = new PanelHeader("Circuits");
      final var row = new JPanel();
      final var buttons = new ArrayList<JButton>();
      for (var i = 0; i < 6; i++) {
        final var button = new JButton();
        button.setPreferredSize(new Dimension(40, 30));
        button.getAccessibleContext().setAccessibleName("Action " + i);
        buttons.add(button);
        row.add(button);
      }
      header.setActions(row);
      final var preferred = header.getPreferredSize();
      layout(header, preferred.width);
      assertTrue(buttons.stream().allMatch(Component::isVisible));
      assertEquals(buttons.size(), visibleButtons(header), "no more button while all fit");

      layout(header, 200);
      assertTrue(buttons.get(0).isVisible(), "the first action should stay in the heading");
      assertFalse(buttons.get(5).isVisible());
      for (final var component : allComponents(header)) {
        if (!component.isVisible() || component.getWidth() == 0) continue;
        final var bounds = SwingUtilities.convertRectangle(
            component.getParent(), component.getBounds(), header);
        assertTrue(bounds.x >= 0 && bounds.x + bounds.width <= header.getWidth(),
            "a heading control was pushed off the edge");
      }

      layout(header, preferred.width);
      assertTrue(buttons.stream().allMatch(Component::isVisible));
    });
  }

  private static void layout(Container container, int width) {
    container.setSize(width, 40);
    for (var pass = 0; pass < 2; pass++) layoutTree(container);
  }

  private static void layoutTree(Container container) {
    container.doLayout();
    for (final var child : container.getComponents()) {
      if (child instanceof Container nested) layoutTree(nested);
    }
  }

  private static long visibleButtons(Container container) {
    return allComponents(container).stream()
        .filter(component -> component instanceof JButton && component.isVisible())
        .count();
  }

  private static List<Component> allComponents(Container container) {
    final var result = new ArrayList<Component>();
    for (final var child : container.getComponents()) {
      result.add(child);
      if (child instanceof Container nested) result.addAll(allComponents(nested));
    }
    return result;
  }
}
