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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import java.awt.Graphics2D;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SimulatorTest {

  private static final String PROPAGATE_ERROR = "propagation stopped with useful context";

  @Test
  void recordsPropagationExceptionMessageAndNotifiesPausedStateOnEdt() throws Exception {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    final var circuit = file.getMainCircuit();
    circuit.setProject(project);
    final var throwingComponent =
        ThrowingFactory.INSTANCE.createComponent(
            Location.create(100, 100, true), ThrowingFactory.INSTANCE.createAttributeSet());
    add(circuit, throwingComponent);

    final var simulator = project.getSimulator();
    try {
      final var propagated = new CountDownLatch(1);
      final var stopped = new CountDownLatch(1);
      final var stoppedOnEdt = new AtomicBoolean();
      simulator.addSimulatorListener(
          new Simulator.Listener() {
            @Override
            public void propagationCompleted(Simulator.Event e) {
              propagated.countDown();
            }

            @Override
            public void simulatorReset(Simulator.Event e) {}

            @Override
            public void simulatorStateChanged(Simulator.Event e) {
              if (simulator.isExceptionEncountered() && !simulator.isAutoPropagating()) {
                stoppedOnEdt.set(SwingUtilities.isEventDispatchThread());
                stopped.countDown();
              }
            }
          });
      simulator.setCircuitState(CircuitState.createRootState(project, circuit));
      SwingUtilities.invokeAndWait(() -> {});

      assertTrue(simulator.nudge());
      assertTrue(propagated.await(2, TimeUnit.SECONDS));
      assertTrue(stopped.await(2, TimeUnit.SECONDS));
      assertTrue(stoppedOnEdt.get());
      assertFalse(simulator.isAutoPropagating());
      assertTrue(simulator.isExceptionEncountered());
      assertEquals(PROPAGATE_ERROR, simulator.getExceptionMessage());
    } finally {
      simulator.shutDown();
    }
  }

  @Test
  void oscillationNotifiesPausedStateOnEdtAndCanRecover() throws Exception {
    final var fixture = new PendingInputFixture();
    final var simulator = fixture.simulator;
    try {
      final var propagator = mock(Propagator.class);
      final var state = mock(CircuitState.class);
      when(state.getPropagator()).thenReturn(propagator);
      when(propagator.getRootState()).thenReturn(fixture.state);
      when(propagator.isOscillating()).thenReturn(true);
      simulator.setCircuitState(state);
      simulator.setAutoPropagation(true);
      SwingUtilities.invokeAndWait(() -> {});

      final var stopped = new CountDownLatch(1);
      final var recovered = new CountDownLatch(1);
      final var stoppedOnEdt = new AtomicBoolean();
      simulator.addSimulatorListener(
          new Simulator.Listener() {
            @Override
            public void simulatorStateChanged(Simulator.Event e) {
              if (simulator.isOscillating() && !simulator.isAutoPropagating()) {
                stoppedOnEdt.set(SwingUtilities.isEventDispatchThread());
                stopped.countDown();
              }
            }

            @Override
            public void simulatorReset(Simulator.Event e) {}

            @Override
            public void propagationCompleted(Simulator.Event e) {
              if (!simulator.isOscillating()) recovered.countDown();
            }
          });

      assertTrue(simulator.nudge());
      assertTrue(stopped.await(2, TimeUnit.SECONDS));
      assertTrue(stoppedOnEdt.get());
      assertFalse(simulator.isAutoPropagating());
      assertTrue(simulator.isOscillating());

      when(propagator.isOscillating()).thenReturn(false);
      simulator.reset();
      simulator.setAutoPropagation(true);
      assertTrue(simulator.nudge());
      assertTrue(recovered.await(2, TimeUnit.SECONDS));
      assertTrue(simulator.isAutoPropagating());
      assertFalse(simulator.isOscillating());
      verify(propagator).reset();
    } finally {
      simulator.shutDown();
    }
  }

  @Test
  void movesPendingInputMarkerWithReplacedComponent() {
    final var fixture = new PendingInputFixture();
    try {
      final var pin =
          Pin.FACTORY.createComponent(
              Location.create(100, 100, true), Pin.FACTORY.createAttributeSet());
      add(fixture.circuit, pin);
      fixture.simulator.addPendingInput(fixture.state, pin);

      final var movedPin =
          Pin.FACTORY.createComponent(Location.create(140, 100, true), pin.getAttributeSet());
      replace(fixture.circuit, pin, movedPin);

      final var graphics = mock(Graphics2D.class);
      fixture.simulator.drawPendingInputs(
          new ComponentDrawContext(
              null, fixture.circuit, fixture.state, graphics, graphics));

      final var bounds = movedPin.getBounds();
      verify(graphics)
          .drawRect(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
    } finally {
      fixture.simulator.shutDown();
    }
  }

  @Test
  void dropsPendingInputMarkerWithRemovedComponent() {
    final var fixture = new PendingInputFixture();
    try {
      final var pin =
          Pin.FACTORY.createComponent(
              Location.create(100, 100, true), Pin.FACTORY.createAttributeSet());
      add(fixture.circuit, pin);
      fixture.simulator.addPendingInput(fixture.state, pin);

      remove(fixture.circuit, pin);

      final var graphics = mock(Graphics2D.class);
      fixture.simulator.drawPendingInputs(
          new ComponentDrawContext(
              null, fixture.circuit, fixture.state, graphics, graphics));

      verify(graphics, never()).drawRect(anyInt(), anyInt(), anyInt(), anyInt());
    } finally {
      fixture.simulator.shutDown();
    }
  }

  private static void add(Circuit circuit, Component component) {
    final var mutation = new CircuitMutation(circuit);
    mutation.add(component);
    mutation.execute();
  }

  private static void remove(Circuit circuit, Component component) {
    final var mutation = new CircuitMutation(circuit);
    mutation.remove(component);
    mutation.execute();
  }

  private static void replace(Circuit circuit, Component oldComponent, Component newComponent) {
    final var mutation = new CircuitMutation(circuit);
    mutation.replace(oldComponent, newComponent);
    mutation.execute();
  }

  private static final class PendingInputFixture {
    private final Circuit circuit;
    private final CircuitState state;
    private final Simulator simulator;

    private PendingInputFixture() {
      final var file = LogisimFile.createNew(new Loader(null), null);
      final var project = new Project(file);
      circuit = file.getMainCircuit();
      circuit.setProject(project);
      state = CircuitState.createRootState(project, circuit);
      simulator = project.getSimulator();
      simulator.setCircuitState(state);
      simulator.setAutoPropagation(false);
    }
  }

  private static final class ThrowingFactory extends InstanceFactory {
    private static final ThrowingFactory INSTANCE = new ThrowingFactory();

    private ThrowingFactory() {
      super("Throwing");
    }

    @Override
    public void paintInstance(InstancePainter painter) {}

    @Override
    public void propagate(InstanceState state) {
      throw new UnsupportedOperationException(PROPAGATE_ERROR);
    }
  }
}
