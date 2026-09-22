/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.RootPaneContainer;
import org.junit.jupiter.api.Test;

class DialogsTest {

  @Test
  void escapeIsBoundInTheFocusedWindowMapOfTheRootPane() {
    final var container = new FakeRootPaneContainer();

    Dialogs.installEscapeToClose(container);

    final var rootPane = container.getRootPane();
    final var key = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    assertEquals(
        Dialogs.CLOSE_ON_ESCAPE, rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(key));
    assertNotNull(rootPane.getActionMap().get(Dialogs.CLOSE_ON_ESCAPE));
  }

  @Test
  void closingAComponentOutsideAnyWindowIsHarmless() {
    assertDoesNotThrow(() -> Dialogs.requestClose(new JPanel()));
  }

  /** Enough of a window to hold a root pane without needing a display. */
  private static class FakeRootPaneContainer implements RootPaneContainer {
    private final JRootPane rootPane = new JRootPane();

    @Override
    public JRootPane getRootPane() {
      return rootPane;
    }

    @Override
    public void setContentPane(Container contentPane) {
      rootPane.setContentPane(contentPane);
    }

    @Override
    public Container getContentPane() {
      return rootPane.getContentPane();
    }

    @Override
    public void setLayeredPane(JLayeredPane layeredPane) {
      rootPane.setLayeredPane(layeredPane);
    }

    @Override
    public JLayeredPane getLayeredPane() {
      return rootPane.getLayeredPane();
    }

    @Override
    public void setGlassPane(Component glassPane) {
      rootPane.setGlassPane(glassPane);
    }

    @Override
    public Component getGlassPane() {
      return rootPane.getGlassPane();
    }
  }
}
