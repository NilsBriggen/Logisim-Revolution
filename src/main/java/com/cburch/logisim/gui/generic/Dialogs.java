/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

/**
 * Keyboard conventions every dialog should follow.
 *
 * <p>Logisim-evolution has no common dialog base class, so these are applied from each dialog's
 * constructor. They are for dialogs only: the main window uses Escape to cancel a drag on the
 * canvas, and must never close on it.
 */
public final class Dialogs {

  /** Name of the action bound to Escape in the root pane's action map. */
  static final String CLOSE_ON_ESCAPE = "logisim.closeOnEscape";

  private Dialogs() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /**
   * Makes Escape close {@code container}'s window, the same way its close box does.
   *
   * <p>Bound in the focused-window map, so a component that handles Escape itself, such as a table
   * cell being edited or an open combo box popup, still sees it first.
   */
  public static void installEscapeToClose(RootPaneContainer container) {
    final var rootPane = container.getRootPane();
    rootPane
        .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CLOSE_ON_ESCAPE);
    rootPane
        .getActionMap()
        .put(
            CLOSE_ON_ESCAPE,
            new AbstractAction() {
              private static final long serialVersionUID = 1L;

              @Override
              public void actionPerformed(ActionEvent event) {
                requestClose(rootPane);
              }
            });
  }

  /**
   * Asks the window containing {@code component} to close, as if its close box were clicked, so
   * the window's own close behaviour and listeners decide what happens. Does nothing when the
   * component is not in a window.
   */
  static void requestClose(Component component) {
    final var window =
        component instanceof Window w ? w : SwingUtilities.getWindowAncestor(component);
    if (window == null) return;
    window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
  }
}
