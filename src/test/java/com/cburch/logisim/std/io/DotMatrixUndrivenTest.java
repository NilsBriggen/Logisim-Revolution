/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import java.awt.Color;
import org.junit.jupiter.api.Test;

/** An unconnected LED matrix shows its dots off, not as one solid red error block. */
class DotMatrixUndrivenTest {

  private static final Color ON = Color.GREEN;
  private static final Color OFF = Color.DARK_GRAY;

  @Test
  void undrivenDotsAreDrawnOff() {
    assertEquals(OFF, DotMatrixBase.dotColor(Value.UNKNOWN, ON, OFF));
    assertEquals(OFF, DotMatrixBase.dotColor(Value.FALSE, ON, OFF));
    assertEquals(ON, DotMatrixBase.dotColor(Value.TRUE, ON, OFF));
    assertEquals(Value.errorColor(), DotMatrixBase.dotColor(Value.ERROR, ON, OFF));
  }

  @Test
  void freshMatrixIsNotAnError() {
    final var state = new DotMatrixBase.State(3, 4, 0);
    for (var row = 0; row < 3; row++) {
      for (var col = 0; col < 4; col++) {
        assertFalse(state.get(row, col, 0).isErrorValue());
      }
    }
  }

  @Test
  void anUndrivenRowSelectLeavesTheRowUndriven() {
    final var state = new DotMatrixBase.State(2, 2, 0);
    state.setSelect(
        Value.createUnknown(BitWidth.create(2)), Value.createKnown(BitWidth.create(2), 3), 1);
    for (var row = 0; row < 2; row++) {
      for (var col = 0; col < 2; col++) {
        assertFalse(state.get(row, col, 0).isErrorValue());
      }
    }
  }

  @Test
  void conflictingRowSelectIsStillAnError() {
    final var state = new DotMatrixBase.State(1, 2, 0);
    state.setSelect(Value.ERROR, Value.createKnown(BitWidth.create(2), 3), 1);
    assertTrue(state.get(0, 0, 0).isErrorValue());
  }
}
