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

import java.io.File;
import org.junit.jupiter.api.Test;

class ExportTableButtonTest {
  @Test
  void appendsMissingExtension() {
    final var result = ExportTableButton.withExtension(new File("truthtable"), ".csv");
    assertEquals("truthtable.csv", result.getName());
  }

  @Test
  void leavesMatchingExtensionAlone() {
    final var result = ExportTableButton.withExtension(new File("truthtable.csv"), ".csv");
    assertEquals("truthtable.csv", result.getName());
  }

  @Test
  void extensionMatchIsCaseInsensitive() {
    final var result = ExportTableButton.withExtension(new File("truthtable.CSV"), ".csv");
    assertEquals("truthtable.CSV", result.getName());
  }
}
