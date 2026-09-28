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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.shell.SectionPanel;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SettingsFormTest {

  /** The x of {@code component} within {@code root}. */
  private static int offsetIn(Container root, Component component) {
    return SwingUtilities.convertPoint(component.getParent(), component.getLocation(), root).x;
  }

  private static void layOut(Container root, int width) {
    root.setSize(width, root.getPreferredSize().height);
    root.doLayout();
    for (final var child : root.getComponents()) {
      if (child instanceof Container container) layOut(container, child.getWidth());
    }
  }

  @Test
  void everyControlStartsAtOneXAndLabelsAndChecksAtTheMargin() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var form = new SettingsForm();
          final var shortLabel = new JLabel("Width:");
          final var longLabel = new JLabel("A considerably longer label:");
          final var first = new JComboBox<>(new String[] {"one"});
          final var second = new JComboBox<>(new String[] {"a much longer choice"});
          final var check = new JCheckBox("Something to switch");
          form.addRow(shortLabel, first);
          form.addFull(check);
          form.addRow(longLabel, second);

          final var nested = form.createNested();
          final var nestedLabel = new JLabel("Inner:");
          final var nestedControl = new JComboBox<>(new String[] {"x"});
          nested.addRow(nestedLabel, nestedControl);
          form.addFull(new SectionPanel("Section", nested, true), true);

          final var page = new JPanel(new java.awt.BorderLayout());
          page.add(form, java.awt.BorderLayout.NORTH);
          layOut(page, 800);

          assertEquals(0, offsetIn(page, shortLabel));
          assertEquals(0, offsetIn(page, longLabel));
          assertEquals(0, offsetIn(page, check));
          assertEquals(0, offsetIn(page, nestedLabel));
          final var controlX = offsetIn(page, first);
          assertTrue(controlX > longLabel.getPreferredSize().width, "controls follow the labels");
          assertEquals(controlX, offsetIn(page, second));
          assertEquals(controlX, offsetIn(page, nestedControl), "a section shares the page's column");
          // Top-aligned rows, in order, with natural-width controls.
          assertTrue(first.getY() < check.getY() && check.getY() < second.getY());
          assertEquals(first.getPreferredSize().width, first.getWidth());
        });
  }

  @Test
  void rowsAreLinkedToTheirLabelsForAssistiveTechnology() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var form = new SettingsForm();
          final var label = new JLabel("Language:");
          final var list = new JList<>(new String[] {"English"});
          form.addRow(label, new JScrollPane(list));
          assertSame(list, label.getLabelFor());
        });
  }

  @Test
  void narrowPagePutsEachLabelAboveItsControl() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var form = new SettingsForm();
          final var label = new JLabel("A label long enough to need its own line:");
          final var control = new JComboBox<>(new String[] {"a fairly long choice"});
          form.addRow(label, control);
          form.addHint("A hint that is long enough that it would otherwise widen the whole page.");
          final var wide = form.getPreferredSize().width;
          assertTrue(form.getMinimumSize().width < wide, "the stacked layout is narrower");

          form.setSize(wide - 20, 400);
          form.doLayout();
          assertTrue(form.isStacked());
          assertEquals(0, control.getX(), "the control moves under its label");
          assertTrue(control.getY() >= label.getY() + label.getHeight());

          form.setSize(wide + 50, 400);
          form.doLayout();
          assertFalse(form.isStacked());
          assertEquals(label.getY() < control.getY() + control.getHeight(), true);
        });
  }
}
