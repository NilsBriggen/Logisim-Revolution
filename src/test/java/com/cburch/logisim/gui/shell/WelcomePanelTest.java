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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class WelcomePanelTest {
  @Test
  void primaryActionsAreNamedFocusableButtonsAndRefreshDoesNotDuplicateActions() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var creates = new AtomicInteger();
      final var opens = new AtomicInteger();
      final var panel = new WelcomePanel(creates::incrementAndGet, opens::incrementAndGet, file -> {});
      panel.refresh();
      final var buttons = new ArrayList<JButton>();
      collectButtons(panel, buttons);
      assertTrue(buttons.size() >= 2);
      for (final var button : buttons) {
        assertTrue(button.isFocusable());
        assertFalse(button.getAccessibleContext().getAccessibleName().isBlank());
      }
      buttons.get(0).doClick(0);
      buttons.get(1).doClick(0);
      assertEquals(1, creates.get());
      assertEquals(1, opens.get());
    });
  }

  private static void collectButtons(Component component, List<JButton> target) {
    if (component instanceof JButton button) target.add(button);
    if (component instanceof Container container) {
      for (final var child : container.getComponents()) collectButtons(child, target);
    }
  }
}
