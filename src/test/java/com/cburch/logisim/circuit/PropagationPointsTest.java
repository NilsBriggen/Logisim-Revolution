/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PropagationPointsTest {

  @Test
  void singleStepMessageUsesTheRightNumberForEachCount() {
    assertEquals(
        "Simulator paused: no signals changed, 1 input change pending",
        PropagationPoints.singleStepMessage(0, 1));
    assertEquals(
        "Simulator paused: 1 signal changed, no input changes pending",
        PropagationPoints.singleStepMessage(1, 0));
    assertEquals(
        "Simulator paused: 3 signals changed, 2 input changes pending",
        PropagationPoints.singleStepMessage(3, 2));
  }

  @Test
  void anEmptyStepSaysNothingChanged() {
    assertEquals(
        "Simulator paused: no signals changed, no input changes pending",
        new PropagationPoints().getSingleStepMessage());
  }
}
