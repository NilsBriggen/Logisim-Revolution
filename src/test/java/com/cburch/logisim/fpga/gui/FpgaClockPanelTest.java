/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FpgaClockPanelTest {
  @Test
  void positiveFactorsAreAccepted() {
    assertEquals(2.5, FpgaClockPanel.parseScalingFactor(" 2.5 "));
    assertEquals(1.0, FpgaClockPanel.parseScalingFactor("1"));
  }

  @Test
  void zeroNegativeNonFiniteAndGarbageAreRejected() {
    for (final var text : new String[] {"0", "0.0", "-1", "NaN", "Infinity", "abc", "", null}) {
      assertTrue(Double.isNaN(FpgaClockPanel.parseScalingFactor(text)), String.valueOf(text));
    }
  }
}
