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
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SettingsNavTest {

  @Test
  void indexesLocalizedLabelsAndTooltipsWithoutIndexingPathValues() {
    final var page = new JPanel();
    page.add(new JLabel("Übergröße"));
    final var nested = new JPanel();
    final var button = new JButton();
    button.setToolTipText("Couleur de la grille");
    nested.add(button);
    nested.add(new JTextField("private-project-path"));
    page.add(nested);

    final var text = SettingsNav.searchableText(page);
    assertTrue(SettingsNav.matches(text, "UBERGROSSE"));
    assertTrue(SettingsNav.matches(text, "grille couleur"));
    assertFalse(SettingsNav.matches(text, "private-project-path"));
  }

  @Test
  void noResultsReportsEmptyPageAndClearingRestoresSelection() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var selected = new ArrayList<Integer>();
          final var nav = new SettingsNav("Filter", selected::add);
          nav.setPages(
              List.of(
                  new SettingsNav.SearchPage("Template", "Load a template"),
                  new SettingsNav.SearchPage("Window", "Theme scale font")));
          nav.setSelectedIndex(1);
          final var filter = findFilter(nav);
          filter.setText("nothing-matches-this");
          filter.flushPendingChange();
          assertTrue(nav.visibleIndexes().isEmpty());
          assertEquals(-1, selected.getLast());

          nav.clearFilter();
          assertEquals(List.of(0, 1), nav.visibleIndexes());
          assertEquals(1, selected.getLast());
        });
  }

  @Test
  void enterFlushesPendingFilterBeforeSelectingMatchingPage() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var selected = new ArrayList<Integer>();
          final var nav = new SettingsNav("Filter", selected::add);
          nav.setPages(
              List.of(
                  new SettingsNav.SearchPage("Template", "Load a template"),
                  new SettingsNav.SearchPage("Window", "Theme scale font")));
          final var filter = findFilter(nav);
          filter.setText("scale");
          filter.getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))
              .actionPerformed(new ActionEvent(filter, ActionEvent.ACTION_PERFORMED, "enter"));
          assertEquals(List.of(1), nav.visibleIndexes());
          assertEquals(1, selected.getLast());
        });
  }

  @Test
  void arrowHandoffUsesFreshResultsAndIgnoresEmptyResults() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var selected = new ArrayList<Integer>();
          final var nav = new SettingsNav("Filter", selected::add);
          nav.setTitles(List.of("Template", "Window", "Window colors"));
          final var filter = findFilter(nav);
          filter.setText("window");
          filter.getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0))
              .actionPerformed(new ActionEvent(filter, ActionEvent.ACTION_PERFORMED, "up"));
          assertEquals(List.of(1, 2), nav.visibleIndexes());
          assertEquals(2, selected.getLast());

          filter.setText("no such setting");
          filter.getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0))
              .actionPerformed(new ActionEvent(filter, ActionEvent.ACTION_PERFORMED, "down"));
          assertTrue(nav.visibleIndexes().isEmpty());
          assertEquals(-1, selected.getLast());
        });
  }

  @Test
  void rebuildingAfterRelocalizationReplacesOldSettingText() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var nav = new SettingsNav("Filter", index -> {});
          final var panel = new JPanel();
          final var label = new JLabel("Theme");
          panel.add(label);
          nav.setPages(
              List.of(new SettingsNav.SearchPage("Window", SettingsNav.searchableText(panel))));
          final var filter = findFilter(nav);
          filter.setText("Darstellung");
          filter.flushPendingChange();
          assertTrue(nav.visibleIndexes().isEmpty());

          label.setText("Darstellung");
          nav.setPages(
              List.of(new SettingsNav.SearchPage("Fenster", SettingsNav.searchableText(panel))));
          assertEquals(List.of(0), nav.visibleIndexes());
        });
  }

  private static FilterField findFilter(Component component) {
    if (component instanceof FilterField field) return field;
    if (component instanceof Container container) {
      for (final var child : container.getComponents()) {
        final var field = findFilter(child);
        if (field != null) return field;
      }
    }
    return null;
  }

  @Test
  void emptyFilterKeepsEveryPage() {
    assertTrue(SettingsNav.matches("Window", ""));
    assertTrue(SettingsNav.matches("Window", "   "));
    assertTrue(SettingsNav.matches("Window", null));
  }

  @Test
  void filteringIgnoresCaseAndSurroundingSpace() {
    assertTrue(SettingsNav.matches("Hotkey settings", "HOTKEY"));
    assertTrue(SettingsNav.matches("Hotkey settings", "  key  "));
    assertFalse(SettingsNav.matches("Hotkey settings", "mouse"));
    assertFalse(SettingsNav.matches(null, "window"));
  }

  @Test
  void theListKeepsThePagesInTheWindowsOwnOrder() {
    final var titles = List.of("Template", "International", "Window", "Layout");
    final var selected = new ArrayList<Integer>();
    final var nav = new SettingsNav("Filter", selected::add);

    nav.setTitles(titles);

    assertEquals(List.of(0, 1, 2, 3), nav.visibleIndexes());
  }

  @Test
  void filteredListStillReportsEachPagesOriginalPosition() {
    // The window shows pages by their own index, so filtering must not renumber them.
    final var nav = new SettingsNav("Filter", index -> {});
    nav.setTitles(List.of("Template", "International", "Window", "Layout"));

    assertEquals(4, nav.visibleIndexes().size());
    assertEquals(Integer.valueOf(2), nav.visibleIndexes().get(2));
  }
}
