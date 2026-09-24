/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildCircuitButtonTest {
  @Test
  void generatedNameAvoidsExistingCircuitsAndSignalsIgnoringCase() {
    assertEquals("main_logic_3", BuildCircuitButton.uniqueCircuitName(
        "main_logic", Set.of("main", "MAIN_LOGIC", "main_logic_2")));
  }

  @Test
  void availableNameIsNotChanged() {
    assertEquals("logic", BuildCircuitButton.uniqueCircuitName("logic", Set.of("main")));
  }
}
