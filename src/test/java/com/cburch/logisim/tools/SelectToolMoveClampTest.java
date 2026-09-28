/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SelectToolMoveClampTest {

  @Test
  void clickWithoutDragOnPartlyOffCanvasSelectionMovesNothing() {
    // A label hanging 15 units left of zero: a click used to push it 15 units right.
    assertEquals(0, SelectTool.clampMove(0, -15));
  }

  @Test
  void partlyOffCanvasSelectionCanMoveBackButNotFurtherOut() {
    assertEquals(30, SelectTool.clampMove(30, -15));
    assertEquals(0, SelectTool.clampMove(-10, -15));
  }

  @Test
  void dragStopsAtZero() {
    assertEquals(-40, SelectTool.clampMove(-100, 40));
    assertEquals(-20, SelectTool.clampMove(-20, 40));
    assertEquals(20, SelectTool.clampMove(20, 40));
  }
}
