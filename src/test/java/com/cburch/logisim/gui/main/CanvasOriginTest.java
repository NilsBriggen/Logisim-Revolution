/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CanvasOriginTest {

  @Test
  void ordinaryCircuitsKeepTheOriginAtZero() {
    assertEquals(0, Canvas.originFor(0));
    assertEquals(0, Canvas.originFor(250));
  }

  @Test
  void contentLeftOfZeroExtendsTheCanvasWithRoomToSpare() {
    assertEquals(-100, Canvas.originFor(-5));
    assertEquals(-100, Canvas.originFor(-80));
    assertEquals(-200, Canvas.originFor(-90));
    for (final var min : new int[] {-1, -37, -99, -150, -1234}) {
      final var origin = Canvas.originFor(min);
      assertTrue(origin <= min - 20, "no margin before " + min);
      assertEquals(0, origin % 100, "origin not on a whole step: " + origin);
    }
  }
}
