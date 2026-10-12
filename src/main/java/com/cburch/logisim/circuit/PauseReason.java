/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit;

/**
 * Why the simulation was asked to pause: a watchpoint was hit or an assertion failed.
 *
 * @param kind what raised the pause
 * @param message a localized, human-readable description
 * @param tick the propagator's half-clock-cycle count when the pause was raised
 * @param source the object that raised it (a component or a watchpoint), may be null
 * @param pausesSimulation whether the interactive simulator should stop its clock; a fault that
 *     does not still fails command-line runs and test vectors
 */
public record PauseReason(
    Kind kind, String message, long tick, Object source, boolean pausesSimulation) {

  /** What raised a pause. */
  public enum Kind {
    WATCHPOINT,
    ASSERTION
  }
}
