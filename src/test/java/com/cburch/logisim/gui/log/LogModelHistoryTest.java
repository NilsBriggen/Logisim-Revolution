/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.vhdl.base.HdlModel;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LogModelHistoryTest {
  @Test
  void hdlSuspendsLoggingAndReturningReusesCircuitHistory() {
    final var state = mock(CircuitState.class);
    final var model = mock(Model.class);
    when(model.getCircuitState()).thenReturn(state);
    final var creations = new AtomicInteger();
    final var history = new LogModelHistory(selected -> {
      assertSame(state, selected);
      creations.incrementAndGet();
      return model;
    });
    assertFalse(history.select(null), "opening timing while already in HDL has no model");
    assertTrue(history.select(state));
    assertFalse(history.select(state), "duplicate simulator/project events must be idempotent");
    assertTrue(history.select(null));
    assertNull(history.current());
    verify(model).setSelected(false);
    assertTrue(history.select(state));
    assertSame(model, history.current());
    org.junit.jupiter.api.Assertions.assertEquals(1, creations.get());
    verify(model, times(2)).setSelected(true);
  }

  @Test
  void realProjectCompletesHdlTransitionBeforeReturningToItsCircuit() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var file = LogisimFile.createNew(new Loader(null), null);
      final var project = new Project(file);
      project.setCurrentCircuit(file.getMainCircuit());
      final var state = project.getCircuitState();
      final var history = new LogModelHistory();
      history.select(state);
      final var original = history.current();
      final var simulator = project.getSimulator();
      final var listener = new Simulator.StatusListener() {
        @Override
        public void simulatorReset(Simulator.Event event) {}

        @Override
        public void simulatorStateChanged(Simulator.Event event) {
          history.select(project.getCircuitState());
        }
      };
      simulator.addSimulatorListener(listener);
      try {
        final var hdl = mock(HdlModel.class);
        assertDoesNotThrow(() -> project.setCurrentHdlModel(hdl));
        assertNull(history.current());
        assertFalse(original.isSelected());
        assertSame(hdl, project.getCurrentHdl());
        verify(hdl).displayChanged();
        project.setCurrentCircuit(file.getMainCircuit());
        assertSame(original, history.current());
        assertTrue(original.isSelected());
      } finally {
        simulator.removeSimulatorListener(listener);
        history.select(null);
        simulator.shutDown();
      }
    });
  }
}
