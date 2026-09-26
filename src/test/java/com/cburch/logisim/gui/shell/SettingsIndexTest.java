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

import com.cburch.logisim.gui.generic.SettingsForm;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import org.junit.jupiter.api.Test;

class SettingsIndexTest {

  @Test
  void readsAFormRowByRowWithSectionsValuesOptionsAndHints() {
    final var form = new SettingsForm();
    final var theme = new JComboBox<>(new String[] {"Light", "Dark", "System"});
    theme.setSelectedIndex(1);
    form.addRow(new JLabel("Theme:"), theme);
    form.addHint("Applies to every window.");
    form.addSection("Simulation");
    final var limit = new JSpinner(new SpinnerNumberModel(1000, 1, 100000, 1));
    form.addRow(new JLabel("Iterations until oscillation"), limit);
    final var check = new JCheckBox("Show tick rate");
    check.setSelected(true);
    form.addFull(check);

    final var settings = SettingsIndex.collect(form);

    assertEquals(
        List.of("Theme", "Iterations until oscillation", "Show tick rate"),
        settings.stream().map(SettingsIndex.Setting::title).toList());
    final var first = settings.get(0);
    assertEquals("", first.section());
    assertEquals("Dark", first.value());
    assertSame(theme, first.control());
    assertTrue(first.keywords().contains("System"), "options are searchable");
    assertTrue(first.keywords().contains("every window"), "the hint is searchable");
    assertEquals("Simulation", settings.get(1).section());
    assertEquals("1000", settings.get(1).value());
    assertEquals("Simulation", settings.get(2).section());
    assertEquals("On", settings.get(2).value());
  }

  @Test
  void findsSettingsInFoldedSectionsButSkipsHiddenControls() {
    final var inner = new SettingsForm();
    final var colour = new JCheckBox("Colour wires by width");
    inner.addFull(colour);
    final var section = new SectionPanel("Canvas", inner, false);
    final var hidden = new JCheckBox("Not offered here");
    hidden.setVisible(false);
    final var form = new SettingsForm();
    form.addFull(section, true);
    form.addFull(hidden);

    final var settings = SettingsIndex.collect(form);

    assertEquals(1, settings.size());
    assertEquals("Colour wires by width", settings.get(0).title());
    assertEquals("Canvas", settings.get(0).section());
    assertEquals("Off", settings.get(0).value());
  }

  @Test
  void readsOtherLayoutsByTheirLabelsAndTitledBorders() {
    final var page = new JPanel();
    final var group = new JPanel();
    group.setBorder(BorderFactory.createTitledBorder("Paths"));
    final var label = new JLabel("Library folder");
    final var field = new JTextField("/opt/lib");
    label.setLabelFor(field);
    // The label comes after its field here: the index goes by labelFor, not by position.
    group.add(field);
    group.add(label);
    page.add(group);

    final var settings = SettingsIndex.collect(page);

    assertEquals(1, settings.size());
    assertEquals("Library folder", settings.get(0).title());
    assertEquals("Paths", settings.get(0).section());
    assertEquals("/opt/lib", settings.get(0).value());
  }

  @Test
  void buttonTextIsItsTitleNotAlsoItsValue() {
    final var form = new SettingsForm();
    form.addFull(new javax.swing.JButton("Reset window layout"));

    final var setting = SettingsIndex.collect(form).get(0);

    assertEquals("Reset window layout", setting.title());
    assertEquals("", setting.value());
  }
}
