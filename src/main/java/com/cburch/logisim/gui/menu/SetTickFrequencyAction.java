/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.menu;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;

/**
 * Changes the auto-tick frequency. The frequency is saved with the circuit, so the change marks
 * the project as modified; as an action it can also be undone, which clears that mark again.
 */
final class SetTickFrequencyAction extends Action {
  private final Simulator simulator;
  private final double frequency;
  private double oldFrequency;

  SetTickFrequencyAction(Simulator simulator, double frequency) {
    this.simulator = simulator;
    this.frequency = frequency;
  }

  @Override
  public void doIt(Project proj) {
    oldFrequency = simulator.getTickFrequency();
    simulator.setTickFrequency(frequency);
  }

  @Override
  public String getName() {
    return S.get("simulateTickFreqAction");
  }

  @Override
  public void undo(Project proj) {
    simulator.setTickFrequency(oldFrequency);
  }
}
