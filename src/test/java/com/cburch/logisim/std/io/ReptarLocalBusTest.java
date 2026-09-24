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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Constant;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ReptarLocalBusTest {
  @Test
  void everyPropagationExplicitlyDrivesOnlyOutputsToWidthCorrectUnknowns() {
    final var factory = new ReptarLocalBus();
    final var state = mock(InstanceState.class);

    factory.propagate(state);
    factory.propagate(state);

    verify(state, times(2)).setPort(ReptarLocalBus.SP6_LB_nCS3_O, Value.UNKNOWN, 1);
    verify(state, times(2)).setPort(ReptarLocalBus.SP6_LB_nADV_ALE_O, Value.UNKNOWN, 1);
    verify(state, times(2)).setPort(ReptarLocalBus.SP6_LB_RE_nOE_O, Value.UNKNOWN, 1);
    verify(state, times(2)).setPort(ReptarLocalBus.SP6_LB_nWE_O, Value.UNKNOWN, 1);
    verify(state, times(2))
        .setPort(ReptarLocalBus.ADDR_DATA_LB_O, Value.createUnknown(BitWidth.create(16)), 1);
    verify(state, times(2))
        .setPort(ReptarLocalBus.ADDR_LB_O, Value.createUnknown(BitWidth.create(9)), 1);
    verifyNoMoreInteractions(state);
  }

  @Test
  void repeatedResetAndPropagationLeaveUnrelatedCircuitRunning() throws Exception {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    final var simulator = project.getSimulator();
    try {
      final var circuit = file.getMainCircuit();
      circuit.setProject(project);
      final var factory = new ReptarLocalBus();
      final var bus =
          factory.createComponent(
              Location.create(100, 100, true), factory.createAttributeSet());
      final var constant =
          Constant.FACTORY.createComponent(
              Location.create(300, 100, true), Constant.FACTORY.createAttributeSet());
      final var mutation = new CircuitMutation(circuit);
      mutation.add(bus);
      mutation.add(constant);
      mutation.execute();
      final var state = CircuitState.createRootState(project, circuit);
      final var completed = new LinkedBlockingQueue<Boolean>();
      simulator.addSimulatorListener(
          new Simulator.Listener() {
            @Override
            public void propagationCompleted(Simulator.Event e) {
              completed.add(true);
            }

            @Override
            public void simulatorReset(Simulator.Event e) {}

            @Override
            public void simulatorStateChanged(Simulator.Event e) {}
          });
      simulator.setCircuitState(state);
      for (var iteration = 0; iteration < 3; iteration++) {
        simulator.reset();
        assertEquals(Boolean.TRUE, completed.poll(2, TimeUnit.SECONDS));
        assertFalse(simulator.isExceptionEncountered());
        assertFalse(simulator.isOscillating());
        assertTrue(simulator.isAutoPropagating());
        assertEquals(Value.TRUE, state.getInstanceState(constant).getPortValue(0));
        final var busState = state.getInstanceState(bus);
        assertEquals(Value.UNKNOWN, busState.getPortValue(ReptarLocalBus.SP6_LB_nCS3_O));
        assertEquals(
            Value.createUnknown(BitWidth.create(16)),
            busState.getPortValue(ReptarLocalBus.ADDR_DATA_LB_O));
        assertEquals(
            Value.createUnknown(BitWidth.create(9)),
            busState.getPortValue(ReptarLocalBus.ADDR_LB_O));
      }
    } finally {
      simulator.shutDown();
    }
  }
}
