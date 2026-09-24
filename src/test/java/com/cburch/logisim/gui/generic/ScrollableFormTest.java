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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScrollableFormTest {
  @ParameterizedTest
  @ValueSource(doubles = {1.0, 1.6, 2.0})
  void flexiblePathFieldKeepsBrowseButtonInsideNarrowViewport(double scale) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var panel = new JPanel(new GridBagLayout());
          final var field = new JTextField(40);
          final var browse = new JButton("Browse");
          field.setFont(field.getFont().deriveFont((float) (12 * scale)));
          browse.setFont(browse.getFont().deriveFont((float) (12 * scale)));
          final var gbc = new GridBagConstraints();
          gbc.gridx = 0;
          gbc.weightx = 1;
          gbc.fill = GridBagConstraints.HORIZONTAL;
          panel.add(field, gbc);
          gbc.gridx = 1;
          gbc.weightx = 0;
          panel.add(browse, gbc);
          final var form = new ScrollableForm(panel);
          final var viewport = new JViewport();
          viewport.setView(form);
          viewport.setSize((int) (300 * scale), (int) (300 * scale));
          layoutTree(viewport);

          assertTrue(form.getScrollableTracksViewportWidth());
          assertEquals(viewport.getWidth(), form.getWidth());
          final var position = SwingUtilities.convertPoint(browse, new Point(0, 0), form);
          assertTrue(position.x >= 0);
          assertTrue(position.x + browse.getWidth() <= viewport.getWidth());
          assertEquals(browse.getPreferredSize().width, browse.getWidth());
          assertEquals(0, panel.getY());
          assertTrue(field.getWidth() < field.getPreferredSize().width);
        });
  }

  @Test
  void unshrinkableFormRemainsScrollableInsteadOfClippingActions() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var panel = new JPanel();
          panel.setPreferredSize(new Dimension(600, 200));
          panel.setMinimumSize(new Dimension(500, 200));
          final var form = new ScrollableForm(panel);
          final var viewport = new JViewport();
          viewport.setView(form);
          viewport.setSize(300, 400);
          layoutTree(viewport);
          assertFalse(form.getScrollableTracksViewportWidth());
          assertTrue(form.getWidth() >= panel.getMinimumSize().width);
        });
  }

  @Test
  void helpWrapsAndFormStaysAtTopOfTallViewport() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var panel = new JPanel();
          panel.setPreferredSize(new Dimension(100, 40));
          final var form = new ScrollableForm(panel);
          form.setHelpText(
              "A long explanation of how these settings affect the current project. ".repeat(4));
          final var viewport = new JViewport();
          viewport.setView(form);
          viewport.setSize(400, 1000);
          layoutTree(viewport);
          final var wideHeight = form.getPreferredSize().height;
          viewport.setSize(180, 1000);
          layoutTree(viewport);
          assertTrue(form.getPreferredSize().height > wideHeight);
          assertEquals(0, panel.getParent().getY());
          assertEquals(40, panel.getHeight());
        });
  }

  @Test
  void screenBoundsAreNeverScaledAlongWithWindowMinima() {
    assertEquals(
        new Dimension(960, 576),
        ScrollableForm.boundedSize(new Dimension(600, 360), new Dimension(1920, 1080), 1.6));
    assertEquals(
        new Dimension(1024, 700),
        ScrollableForm.boundedSize(new Dimension(600, 360), new Dimension(1024, 700), 2.0));
  }

  private static void layoutTree(Container container) {
    container.doLayout();
    for (final var child : container.getComponents()) {
      if (child instanceof Container nested) layoutTree(nested);
    }
  }
}
