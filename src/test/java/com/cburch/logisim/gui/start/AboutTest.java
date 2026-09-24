/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.start;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.cburch.logisim.util.UiScale;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class AboutTest {
  @Test
  void creditsTimerStopsOnHideAndRestartsAfterReparenting() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var first = new JPanel();
          final var second = new JPanel();
          final var panel = new About.AboutPanel(true);
          assertFalse(panel.isAnimationRunning());
          try {
            first.addNotify();
            second.addNotify();
            for (var iteration = 0; iteration < 3; iteration++) {
              first.add(panel);
              assertTrue(panel.isAnimationRunning());
              panel.setVisible(false);
              assertFalse(panel.isAnimationRunning());
              panel.setVisible(true);
              assertTrue(panel.isAnimationRunning());
              first.remove(panel);
              assertFalse(panel.isAnimationRunning());
              second.add(panel);
              assertTrue(panel.isAnimationRunning());
              second.remove(panel);
              assertFalse(panel.isAnimationRunning());
            }
          } finally {
            first.removeNotify();
            second.removeNotify();
          }
        });
  }

  @Test
  void splashPanelHasScaledPaddingAndNeverStartsCreditsTimer() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var panel = new About.AboutPanel(false);
          try {
            panel.addNotify();
            assertFalse(panel.isAnimationRunning());
            assertEquals(UiScale.scaled(About.PADDING), panel.getInsets().left);
            assertEquals(
                UiScale.scaled(About.PANEL_WIDTH + 2 * About.PADDING),
                panel.getPreferredSize().width);
            assertEquals(
                UiScale.scaled(About.LOGO_HEIGHT + 2 * About.PADDING),
                panel.getPreferredSize().height);
          } finally {
            panel.removeNotify();
          }
        });
  }

  @Test
  void dialogDisposesOnEscapeAndDefaultsToClose() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    SwingUtilities.invokeAndWait(
        () -> {
          final var dialog = About.createAboutDialog(null);
          try {
            assertEquals(JDialog.DISPOSE_ON_CLOSE, dialog.getDefaultCloseOperation());
            assertFalse(dialog.isModal());
            final var root = dialog.getRootPane();
            assertTrue(root.getDefaultButton() != null);
            final var pane = (JOptionPane) dialog.getContentPane();
            final var escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
            final var rootKey = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(escape);
            final var ancestorKey = pane.getInputMap(
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(escape);
            assertSame(root.getActionMap().get(rootKey), pane.getActionMap().get(ancestorKey));
            // Exercise Swing's child -> ancestor -> window binding resolution, not the root
            // action directly: JOptionPane's default ancestor action used to swallow this key.
            dialog.setVisible(true);
            final var copyButton = (JButton) pane.getOptions()[0];
            assertTrue(SwingUtilities.processKeyBindings(new KeyEvent(
                copyButton, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)));
            assertFalse(dialog.isDisplayable());
          } finally {
            dialog.dispose();
          }
        });
  }
}
