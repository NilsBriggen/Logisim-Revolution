/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.std.Builtin;
import com.cburch.logisim.std.base.BaseLibrary;
import com.cburch.logisim.tools.AddTool;
import org.junit.jupiter.api.Test;

/** How the palette decides which group a component belongs in. */
class PaletteCatalogTest {

  private static PaletteCatalog.Group group(String library, String tool) {
    return PaletteCatalog.groupOf(library, tool).orElseThrow();
  }

  @Test
  void libraryDecidesForItsComponentsByDefault() {
    assertEquals(PaletteCatalog.Group.LOGIC, group("Gates", "AND Gate"));
    assertEquals(PaletteCatalog.Group.ARITHMETIC, group("Arithmetic", "Adder"));
    assertEquals(PaletteCatalog.Group.MEMORY, group("Memory", "RAM"));
    assertEquals(PaletteCatalog.Group.CHIPS, group("TTL", "7400"));
  }

  /**
   * Two libraries hold components of more than one kind, which is the reason the table exists:
   * grouping by library alone would file a pin next to a splitter and an LED next to a keyboard.
   */
  @Test
  void componentCanOverrideItsLibrary() {
    assertEquals(PaletteCatalog.Group.ROUTING, group("Wiring", "Splitter"));
    assertEquals(PaletteCatalog.Group.INPUT_OUTPUT, group("Wiring", "Pin"));
    assertEquals(PaletteCatalog.Group.INPUT_OUTPUT, group("Wiring", "Clock"));

    assertEquals(PaletteCatalog.Group.INPUT_OUTPUT, group("I/O", "Button"));
    assertEquals(PaletteCatalog.Group.DISPLAYS, group("I/O", "LED"));
    assertEquals(PaletteCatalog.Group.DISPLAYS, group("I/O", "7-Segment Display"));
  }

  /** Identifiers are compared without regard to case or stray spaces. */
  @Test
  void identifiersAreMatchedLoosely() {
    assertEquals(PaletteCatalog.Group.LOGIC, group("gates", "and gate"));
    assertEquals(PaletteCatalog.Group.DISPLAYS, group(" I/O ", " led "));
  }

  /**
   * A library the palette has never heard of keeps all of its components, under its own name. That
   * is what makes the table safe to ship: it can only improve on the grouping, never lose a
   * component.
   */
  @Test
  void unknownLibraryFallsBackToItsOwnName() {
    assertTrue(PaletteCatalog.groupOf("Some Third Party Library", "Widget").isEmpty());
    assertFalse(PaletteCatalog.knowsLibrary("Some Third Party Library"));
    assertTrue(PaletteCatalog.groupOf(null, null).isEmpty());
  }

  /** An unknown component of a known library still lands in that library's group. */
  @Test
  void unknownComponentOfKnownLibraryUsesTheLibrarysGroup() {
    assertEquals(PaletteCatalog.Group.LOGIC, group("Gates", "Some Gate Added Later"));
  }

  /** Every built-in library except the editing tools is placed, so nothing falls through. */
  @Test
  void everyBuiltInLibraryIsPlaced() {
    for (final var library : new Builtin().getLibraries()) {
      if (BaseLibrary._ID.equals(library.getName())) continue;
      assertTrue(
          PaletteCatalog.knowsLibrary(library.getName()),
          "no group for the built-in library " + library.getName());
    }
  }

  /** And every component in them lands somewhere, which is what the palette shows. */
  @Test
  void everyBuiltInComponentLandsInAGroup() {
    for (final var library : new Builtin().getLibraries()) {
      if (BaseLibrary._ID.equals(library.getName())) continue;
      for (final var tool : library.getTools()) {
        if (!(tool instanceof AddTool)) continue;
        assertTrue(
            PaletteCatalog.groupOf(library.getName(), tool.getName()).isPresent(),
            "no group for " + library.getName() + " / " + tool.getName());
      }
    }
  }

  @Test
  void everyGroupHasAName() {
    for (final var value : PaletteCatalog.Group.values()) {
      final var title = value.title();
      assertFalse(title == null || title.isBlank(), value + " has no title");
      assertFalse(title.startsWith("paletteGroup"), value + " has no translation: " + title);
    }
  }
}
