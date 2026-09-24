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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The lists the palette keeps at the top: the pinned components and the ones used last. */
class PaletteMemoryTest {

  private static final String AND = PaletteMemory.key("Gates", "AND Gate");
  private static final String OR = PaletteMemory.key("Gates", "OR Gate");
  private static final String PIN = PaletteMemory.key("Wiring", "Pin");

  @Test
  void keyNamesBothTheLibraryAndTheComponent() {
    assertFalse(AND.equals(PaletteMemory.key("Plexers", "AND Gate")));
    assertEquals(AND, PaletteMemory.key("Gates", "AND Gate"));
  }

  /** The separators are the two characters the encoding uses, so a name cannot contain them. */
  @Test
  void keyCannotBeBrokenByAnAwkwardName() {
    final var key = PaletteMemory.key("Od/d\nLibrary", "We\nird/Name");
    assertEquals(1, key.chars().filter(character -> character == '/').count());
    assertFalse(key.contains("\n"));
  }

  @Test
  void storedListSurvivesBeingWrittenAndReadBack() {
    final var keys = List.of(AND, OR, PIN);
    assertEquals(keys, PaletteMemory.decode(PaletteMemory.encode(keys)));
  }

  @Test
  void blanksAndRepeatsAreIgnoredWhenReading() {
    assertEquals(List.of(), PaletteMemory.decode(null));
    assertEquals(List.of(), PaletteMemory.decode("   "));
    assertEquals(List.of(AND, OR), PaletteMemory.decode(AND + "\n\n" + OR + "\n" + AND));
  }

  @Test
  void usingAComponentPutsItFirst() {
    var recents = List.of(AND, OR, PIN);
    recents = PaletteMemory.withMostRecent(recents, PIN);
    assertEquals(List.of(PIN, AND, OR), recents);
  }

  @Test
  void usingAComponentAgainDoesNotListItTwice() {
    var recents = PaletteMemory.withMostRecent(List.of(), AND);
    recents = PaletteMemory.withMostRecent(recents, AND);
    assertEquals(List.of(AND), recents);
  }

  /** Otherwise the list, and the preference holding it, would grow for ever. */
  @Test
  void theRecentListStopsGrowing() {
    var recents = List.<String>of();
    for (var index = 0; index < PaletteMemory.RECENT_LIMIT * 3; index++) {
      recents = PaletteMemory.withMostRecent(recents, PaletteMemory.key("Gates", "Gate " + index));
    }
    assertEquals(PaletteMemory.RECENT_LIMIT, recents.size());
    assertEquals(PaletteMemory.key("Gates", "Gate 35"), recents.get(0), "newest first");
  }

  @Test
  void pinningIsItsOwnOpposite() {
    var favourites = PaletteMemory.toggled(List.of(), AND);
    assertEquals(List.of(AND), favourites);
    favourites = PaletteMemory.toggled(favourites, OR);
    assertEquals(List.of(AND, OR), favourites);
    favourites = PaletteMemory.toggled(favourites, AND);
    assertEquals(List.of(OR), favourites);
  }

  /** A library can be uninstalled; the palette should not show a gap where its components were. */
  @Test
  void componentsThatAreNoLongerInstalledAreDropped() {
    final var kept = PaletteMemory.retaining(List.of(AND, OR, PIN), Set.of(AND, PIN));
    assertEquals(List.of(AND, PIN), kept);
  }

  @Test
  void theListsAreNotChangedInPlace() {
    final var original = new ArrayList<>(List.of(AND, OR));
    PaletteMemory.withMostRecent(original, PIN);
    PaletteMemory.toggled(original, PIN);
    PaletteMemory.retaining(original, Set.of(AND));
    assertEquals(List.of(AND, OR), original);
    assertTrue(PaletteMemory.decode(PaletteMemory.encode(original)).contains(OR));
  }
}
