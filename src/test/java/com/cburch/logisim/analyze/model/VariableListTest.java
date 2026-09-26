/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The maximum size of a variable list is a number of bits, not of variables. */
class VariableListTest {

  @Test
  void addCountsBitsNotVariables() {
    final var list = new VariableList(8);
    list.add(new Var("a", 4));
    list.add(new Var("b", 4));
    assertEquals(8, list.bits.size());
    // Used to be accepted: 2 variables + 1 bit <= 8.
    assertThrows(IllegalArgumentException.class, () -> list.add(new Var("c", 1)));
    assertEquals(8, list.bits.size());
  }

  @Test
  void replaceCountsTheBitsItFrees() {
    final var list = new VariableList(8);
    final var a = new Var("a", 4);
    list.add(a);
    list.add(new Var("b", 2));
    final var wider = new Var("a", 6);
    assertDoesNotThrow(() -> list.replace(a, wider));
    assertEquals(8, list.bits.size());
    assertThrows(IllegalArgumentException.class, () -> list.replace(wider, new Var("a", 7)));
    assertEquals(8, list.bits.size());
  }
}
