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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import java.util.List;
import org.junit.jupiter.api.Test;

class PropagatorFaultQueueTest {

  private static PauseReason fault(String message) {
    return new PauseReason(PauseReason.Kind.ASSERTION, message, 0, null, true);
  }

  private static Propagator newPropagator() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    final var circuit = file.getMainCircuit();
    circuit.setProject(project);
    return CircuitState.createRootState(project, circuit, Thread.currentThread()).getPropagator();
  }

  @Test
  void drainReturnsFaultsInOrderAndEmptiesTheQueue() {
    final var prop = newPropagator();
    prop.reportFault(fault("a"));
    prop.reportFault(fault("b"));
    prop.reportFault(null);

    assertEquals(List.of("a", "b"), prop.drainFaults().stream().map(PauseReason::message).toList());
    assertTrue(prop.drainFaults().isEmpty());
  }

  @Test
  void resetDiscardsPendingFaults() {
    final var prop = newPropagator();
    prop.reportFault(fault("stale"));
    prop.reset();

    assertTrue(prop.drainFaults().isEmpty());
  }
}
