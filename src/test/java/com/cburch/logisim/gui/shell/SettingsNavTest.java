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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingsNavTest {

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
