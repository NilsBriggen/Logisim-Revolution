/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.PauseReason;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import org.junit.jupiter.api.Test;

class AssertionTest {

  @Test
  void failsOnlyWhenEnabledAndConditionIsLow() {
    assertTrue(Assertion.fails(Value.FALSE, Value.TRUE, false));
    assertFalse(Assertion.fails(Value.TRUE, Value.TRUE, false));
    assertFalse(Assertion.fails(Value.FALSE, Value.FALSE, false));
    assertFalse(Assertion.fails(Value.UNKNOWN, Value.TRUE, false));
    assertTrue(Assertion.fails(Value.UNKNOWN, Value.TRUE, true));
    assertTrue(Assertion.fails(Value.ERROR, Value.UNKNOWN, true));
  }

  @Test
  void reportsOnceWhenTheConditionStartsFailing() {
    final var fixture = new Fixture(false);
    final var prop = fixture.state.getPropagator();

    fixture.drive(fixture.condition, Value.TRUE);
    assertTrue(prop.drainFaults().isEmpty());

    fixture.drive(fixture.condition, Value.FALSE);
    final var faults = prop.drainFaults();
    assertEquals(1, faults.size());
    final var fault = faults.getFirst();
    assertEquals(PauseReason.Kind.ASSERTION, fault.kind());
    assertTrue(fault.pausesSimulation());
    assertTrue(fault.message().contains("chk"), fault.message());
    assertTrue(fault.message().contains("must be high"), fault.message());

    // Still failing: no new report until it recovers and fails again.
    fixture.state.markComponentAsDirty(fixture.assertion);
    fixture.state.getPropagator().propagate();
    assertTrue(prop.drainFaults().isEmpty());
    fixture.drive(fixture.condition, Value.TRUE);
    fixture.drive(fixture.condition, Value.FALSE);
    assertEquals(1, prop.drainFaults().size());
  }

  @Test
  void enableInputGatesTheCheck() {
    final var fixture = new Fixture(true);
    final var prop = fixture.state.getPropagator();

    fixture.drive(fixture.enable, Value.FALSE);
    fixture.drive(fixture.condition, Value.FALSE);
    assertTrue(prop.drainFaults().isEmpty());

    fixture.drive(fixture.enable, Value.TRUE);
    assertEquals(1, prop.drainFaults().size());
  }

  @Test
  void logOnlyActionDoesNotPauseTheSimulator() {
    final var fixture = new Fixture(false);
    fixture.assertion.getAttributeSet().setValue(Assertion.ATTR_ACTION, Assertion.ACTION_LOG);
    fixture.drive(fixture.condition, Value.TRUE);
    fixture.drive(fixture.condition, Value.FALSE);

    final var faults = fixture.state.getPropagator().drainFaults();
    assertEquals(1, faults.size());
    assertFalse(faults.getFirst().pausesSimulation());
  }

  /** An input pin wired to the assertion's condition, and optionally one to its enable. */
  private static final class Fixture {
    final Component assertion;
    final Component condition;
    final Component enable;
    final CircuitState state;

    Fixture(boolean withEnable) {
      final var file = LogisimFile.createNew(new Loader(null), null);
      final var project = new Project(file);
      final Circuit circuit = file.getMainCircuit();
      circuit.setProject(project);
      final var attrs = Assertion.FACTORY.createAttributeSet();
      attrs.setValue(StdAttr.LABEL, "chk");
      attrs.setValue(Assertion.ATTR_MESSAGE, "must be high");
      assertion = Assertion.FACTORY.createComponent(Location.create(200, 100, true), attrs);
      condition = Pin.FACTORY.createComponent(Location.create(100, 100, true), Pin.FACTORY.createAttributeSet());
      final var mutation = new CircuitMutation(circuit);
      mutation.add(assertion);
      mutation.add(condition);
      mutation.add(Wire.create(Location.create(100, 100, true), Location.create(200, 100, true)));
      if (withEnable) {
        // Facing east, the enable input is a quarter turn clockwise: above the body's right edge.
        final var enableEnd = assertion.getEnds().get(Assertion.ENABLE).getLocation();
        final var pinAt = Location.create(enableEnd.getX(), enableEnd.getY() - 40, true);
        final var pinAttrs = Pin.FACTORY.createAttributeSet();
        pinAttrs.setValue(StdAttr.FACING, com.cburch.logisim.data.Direction.SOUTH);
        enable = Pin.FACTORY.createComponent(pinAt, pinAttrs);
        mutation.add(enable);
        mutation.add(Wire.create(pinAt, enableEnd));
      } else {
        enable = null;
      }
      mutation.execute();
      state = CircuitState.createRootState(project, circuit, Thread.currentThread());
      state.getPropagator().propagate();
      // Input pins start low, so the assertion has already failed once; start from a clean slate.
      state.getPropagator().drainFaults();
    }

    void drive(Component pin, Value value) {
      final var pinState = state.getInstanceState(pin);
      Pin.FACTORY.driveInputPin(pinState, value);
      state.markComponentAsDirty(pin);
      state.getPropagator().propagate();
    }
  }
}
