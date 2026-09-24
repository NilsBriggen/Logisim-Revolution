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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaletteSearchTest {
  @Test
  void aliasesFindStableToolsEvenWithTranslatedLabels() {
    assertTrue(PaletteSearch.score("Multiplexer", "Multiplexeur", "MUX") >= 0);
    assertTrue(PaletteSearch.score("Demultiplexer", "Démultiplexeur", "demux") >= 0);
    assertTrue(PaletteSearch.score("Pin", "Anschluss", "output pin") >= 0);
    assertTrue(PaletteSearch.score("D Flip-Flop", "Bascule D", "dff") >= 0);
    assertTrue(PaletteSearch.score("D Flip-Flop", "Bascule D", "flip flop") >= 0);
    assertTrue(PaletteSearch.score("PlaRom", "PLA", "pla rom") >= 0);
  }

  @Test
  void exactAndAndMuxRankBeforeRelatedSubstringMatches() {
    assertTrue(PaletteSearch.score("AND Gate", "AND", "and")
        < PaletteSearch.score("NAND Gate", "NAND", "and"));
    assertTrue(PaletteSearch.score("Multiplexer", "Multiplexer", "mux")
        < PaletteSearch.score("Demultiplexer", "Demultiplexer", "mux"));
    assertEquals(-1, PaletteSearch.score("Multiplexer", "Multiplexer", "no-such-part"));
  }
}
