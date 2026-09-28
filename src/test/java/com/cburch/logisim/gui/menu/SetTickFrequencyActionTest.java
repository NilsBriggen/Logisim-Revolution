/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.proj.Project;
import org.junit.jupiter.api.Test;

class SetTickFrequencyActionTest {

  @Test
  void changingTheTickFrequencyIsAnUndoableModification() {
    final var simulator = mock(Simulator.class);
    final var project = mock(Project.class);
    when(simulator.getTickFrequency()).thenReturn(4.0);
    final var action = new SetTickFrequencyAction(simulator, 128.0);

    assertTrue(action.isModification());
    action.doIt(project);
    action.undo(project);

    final var order = inOrder(simulator);
    order.verify(simulator).setTickFrequency(128.0);
    order.verify(simulator).setTickFrequency(4.0);
  }
}
