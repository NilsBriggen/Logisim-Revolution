/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ModelUpdateQueueTest {

  @Test
  void samplesOnPostButChangesTheModelOnlyWhenDrained() throws Exception {
    final var fixture = new Fixture();
    final var scheduled = new ArrayList<Runnable>();
    final var queue = new ModelUpdateQueue(scheduled::add);
    final var threads = new ArrayList<String>();
    fixture.model.addModelListener(new Model.Listener() {
      @Override
      public void signalsExtended(Model.Event event) {
        threads.add(Thread.currentThread().getName());
      }
    });
    final var endBefore = fixture.model.getEndTime();

    // Post from another thread, as the simulator does.
    final var simThread = new Thread(() -> {
      queue.propagationCompleted(fixture.model, false, false, true);
      fixture.drive(Value.TRUE);
      queue.propagationCompleted(fixture.model, false, false, true);
    }, "fake-sim-thread");
    simThread.start();
    simThread.join();

    assertEquals(endBefore, fixture.model.getEndTime(), "model changed off the event thread");
    assertTrue(threads.isEmpty());
    assertEquals(1, scheduled.size(), "posts are coalesced into one invokeLater");
    assertEquals(2, queue.pendingCount());

    scheduled.get(0).run();

    assertEquals(0, queue.pendingCount());
    assertEquals(List.of(Thread.currentThread().getName(), Thread.currentThread().getName()),
        threads);
    // The first sample was taken while the pin was still low, and is recorded as such.
    final var signal = fixture.model.getSignal(0);
    final var step = fixture.model.getTimeScale();
    assertEquals(Value.FALSE, signal.getValue(endBefore + step - 1));
    assertEquals(Value.TRUE, signal.getValue(fixture.model.getEndTime() - 1));
  }

  @Test
  void schedulesAgainAfterADrain() {
    final var fixture = new Fixture();
    final var scheduled = new ArrayList<Runnable>();
    final var queue = new ModelUpdateQueue(scheduled::add);

    queue.propagationCompleted(fixture.model, false, false, true);
    scheduled.get(0).run();
    queue.simulatorReset(fixture.model);

    assertEquals(2, scheduled.size());
  }

  @Test
  void resetAndPropagationKeepTheirOrder() {
    final var fixture = new Fixture();
    final var scheduled = new ArrayList<Runnable>();
    final var queue = new ModelUpdateQueue(scheduled::add);
    final var events = new ArrayList<String>();
    fixture.model.addModelListener(new Model.Listener() {
      @Override
      public void signalsExtended(Model.Event event) {
        events.add("extended");
      }

      @Override
      public void signalsReset(Model.Event event) {
        events.add("reset");
      }
    });

    queue.propagationCompleted(fixture.model, false, false, true);
    queue.simulatorReset(fixture.model);
    queue.propagationCompleted(fixture.model, false, false, true);
    scheduled.get(0).run();

    assertEquals(List.of("extended", "reset", "extended"), events);
  }

  @Test
  void skipsEventsTheModelWouldIgnore() {
    final var fixture = new Fixture();
    final var calls = new AtomicInteger();
    final var queue = new ModelUpdateQueue(r -> calls.incrementAndGet());

    // A nudge that changed nothing, and a coarse-mode transient.
    queue.propagationCompleted(fixture.model, false, false, false);
    queue.propagationCompleted(fixture.model, false, true, false);

    assertEquals(0, calls.get());
    assertEquals(0, queue.pendingCount());
    assertFalse(fixture.model.isFine());
  }

  private static final class Fixture {
    private final Model model;
    private final com.cburch.logisim.circuit.CircuitState state;
    private final com.cburch.logisim.comp.Component pin;

    private Fixture() {
      final var file = LogisimFile.createNew(new Loader(null), null);
      final var project = new Project(file);
      final var circuit = file.getMainCircuit();
      circuit.setProject(project);
      project.setCurrentCircuit(circuit);
      state = project.getCircuitState();
      pin = Pin.FACTORY.createComponent(
          Location.create(100, 100, true), Pin.FACTORY.createAttributeSet());
      final var mutation = new CircuitMutation(circuit);
      mutation.add(pin);
      mutation.execute();
      drive(Value.FALSE);
      model = new Model(state);
    }

    void drive(Value value) {
      Pin.FACTORY.driveInputPin(state.getInstanceState(pin), value);
    }
  }
}
