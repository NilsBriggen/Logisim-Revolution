/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.vhdl.base.HdlModel;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class TestFrameLifecycleTest {
  @Test
  void projectHdlRoundTripPausesTestingWithoutLosingTheCircuitModel() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var file = LogisimFile.createNew(new Loader(null), null);
      final var project = new Project(file);
      project.setCurrentCircuit(file.getMainCircuit());
      final var history = new TestFrame.ModelHistory(
          circuit -> new Model(project, circuit), mock(ModelListener.class));
      final var original = history.select(project.getCurrentCircuit());
      final ProjectListener listener = event -> {
        if (event.getAction() == ProjectEvent.ACTION_SET_STATE) {
          history.select(event.getProject().getCurrentCircuit());
        }
      };
      project.addProjectListener(listener);
      try {
        final var hdl = mock(HdlModel.class);
        assertDoesNotThrow(() -> project.setCurrentHdlModel(hdl));
        assertFalse(original.isSelected());
        assertTrue(original.isPaused());
        verify(hdl).displayChanged();
        project.setCurrentCircuit(file.getMainCircuit());
        assertSame(original, history.select(project.getCurrentCircuit()));
        assertTrue(original.isSelected());
      } finally {
        history.select(null);
        project.removeProjectListener(listener);
        project.getSimulator().shutDown();
      }
    });
  }

  @Test
  void hdlDeselectionPausesOldModelAndReturningRestoresItsResults() {
    final var circuit = mock(Circuit.class);
    final var model = mock(Model.class);
    when(model.getCircuit()).thenReturn(circuit);
    final var listener = mock(ModelListener.class);
    final var calls = new AtomicInteger();
    final var history = new TestFrame.ModelHistory(selected -> {
      assertSame(circuit, selected);
      calls.incrementAndGet();
      return model;
    }, listener);
    assertNull(history.select(null));
    assertSame(model, history.select(circuit));
    assertNull(history.select(null));
    verify(model).removeModelListener(listener);
    verify(model).setSelected(false);
    assertSame(model, history.select(circuit));
    org.junit.jupiter.api.Assertions.assertEquals(1, calls.get());
  }

  @Test
  void hdlDisablesEveryModelActionAndCircuitReenablesLoading() {
    final var load = new JButton();
    final var run = new JButton();
    final var stop = new JButton();
    final var reset = new JButton();
    TestFrame.updateControls(null, load, run, stop, reset);
    assertFalse(load.isEnabled());
    assertFalse(run.isEnabled());
    assertFalse(stop.isEnabled());
    assertFalse(reset.isEnabled());
    TestFrame.updateControls(mock(Model.class), load, run, stop, reset);
    assertTrue(load.isEnabled());
    assertFalse(run.isEnabled());
  }
}
