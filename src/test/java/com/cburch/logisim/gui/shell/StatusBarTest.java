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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class StatusBarTest {

  @Test
  void breadcrumbsAndZoomHaveFocusableKeyboardActions() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var bar = new StatusBar();
      final var activated = new ArrayList<Integer>();
      bar.setCircuitTrail(List.of("main", "ALU"), activated::add);
      bar.setZoom("100%");
      bar.setZoomAction(() -> activated.add(2));
      final var buttons = new ArrayList<JButton>();
      collectButtons(bar, buttons);
      assertEquals(3, buttons.size());
      assertEquals("main", buttons.get(0).getAccessibleContext().getAccessibleName());
      assertEquals("ALU", buttons.get(1).getAccessibleContext().getAccessibleName());
      for (final var button : buttons) {
        assertTrue(button.isFocusable());
        assertTrue(button.isFocusPainted());
        for (final var released : new boolean[] {false, true}) {
          final var key = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, released);
          final var binding = button.getInputMap(JComponent.WHEN_FOCUSED).get(key);
          assertNotNull(binding, "button must support Space");
          final var action = button.getActionMap().get(binding);
          assertNotNull(action);
          action.actionPerformed(new ActionEvent(button, ActionEvent.ACTION_PERFORMED, ""));
        }
      }
      assertEquals(List.of(0, 1, 2), activated);
      bar.setZoomAction(() -> activated.add(3));
      buttons.get(2).doClick(0);
      assertEquals(List.of(0, 1, 2, 3), activated, "replacing action must not duplicate listeners");
    });
  }

  private static void collectButtons(Container parent, List<JButton> buttons) {
    for (final var child : parent.getComponents()) {
      if (child instanceof JButton button) buttons.add(button);
      else if (child instanceof Container container) collectButtons(container, buttons);
    }
  }
}

