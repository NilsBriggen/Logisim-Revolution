/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.vhdl.base;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VhdlParserTest {
  private static final String SOURCE =
      "-- header comment\n"
          + "\n"
          + "library ieee;\n"
          + "use ieee.std_logic_1164.all;\n"
          + "\n"
          + "entity demo is\n"
          + "  port (\n"
          + "    a : in std_logic;\n"
          + "    q : %s std_logic\n"
          + "  );\n"
          + "end demo;\n"
          + "\n"
          + "architecture rtl of demo is\n"
          + "begin\n"
          + "end rtl;\n";

  @Test
  void validEntityStillParses() {
    final var parser = new VhdlParser(String.format(SOURCE, "out"));
    assertDoesNotThrow(parser::parse);
    assertEquals("demo", parser.getName());
    assertEquals(1, parser.getInputs().size());
    assertEquals(1, parser.getOutputs().size());
  }

  @Test
  void portErrorNamesTheLineCountingLeadingCommentsAndBlankLines() {
    final var parser = new VhdlParser(String.format(SOURCE, "outt"));
    final var error = assertThrows(VhdlParser.IllegalVhdlContentException.class, parser::parse);
    assertTrue(error.getMessage().startsWith("Line 9:"), error.getMessage());
    assertTrue(error.getMessage().contains("outt"), error.getMessage());
  }
}
