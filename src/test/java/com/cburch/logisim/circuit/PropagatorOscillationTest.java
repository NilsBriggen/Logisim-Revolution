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

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.Options;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.gates.GatesLibrary;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.PullResistor;
import com.cburch.logisim.tools.AddTool;
import org.junit.jupiter.api.Test;

class PropagatorOscillationTest {

  private static final int DEPTH = 110;
  private static final int LIMIT = 100;

  @Test
  void deeplyNestedStableCircuitIsNotReportedAsOscillating() {
    final var project = newProject();
    final var top = project.getLogisimFile().getMainCircuit();
    final var io = fill(top, nest(project, DEPTH));
    final var state = CircuitState.createRootState(project, top, Thread.currentThread());
    final var prop = state.getPropagator();

    prop.propagate();
    assertFalse(prop.isOscillating());
    assertEquals(Value.TRUE, state.getValue(io[1].getLocation()));

    final var in = state.getInstanceState(io[0]);
    Pin.FACTORY.driveInputPin(in, Value.TRUE);
    Pin.FACTORY.propagate(in);
    prop.propagate();
    assertFalse(prop.isOscillating());
    assertEquals(Value.FALSE, state.getValue(io[1].getLocation()));
  }

  @Test
  void deeplyNestedFeedbackLoopIsStillReportedAsOscillating() {
    final var project = newProject();
    final var top = project.getLogisimFile().getMainCircuit();
    final var inner = nest(project, DEPTH);
    final var mid = inner.getSubcircuitFactory()
        .createComponent(Location.create(200, 100, true), inner.getSubcircuitFactory().createAttributeSet());
    add(top, mid);
    add(top, Wire.create(end(mid, false), end(mid, true)));
    // The pull resistor gives the loop a defined starting value, so the inverter really oscillates.
    add(top, PullResistor.FACTORY.createComponent(end(mid, false), PullResistor.FACTORY.createAttributeSet()));
    final var state = CircuitState.createRootState(project, top, Thread.currentThread());

    state.getPropagator().propagate();
    assertTrue(state.getPropagator().isOscillating());
  }

  private static Project newProject() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    final var top = file.getMainCircuit();
    top.setProject(project);
    project.setCurrentCircuit(top);
    project.getOptions().getAttributeSet().setValue(Options.ATTR_SIM_LIMIT, LIMIT);
    return project;
  }

  /** Returns a circuit holding a NOT gate wrapped in {@code depth} levels of subcircuits. */
  private static Circuit nest(Project project, int depth) {
    final var file = project.getLogisimFile();
    Circuit inner = null;
    for (var level = 0; level < depth; level++) {
      final var c = new Circuit("c" + level, file, project);
      file.addCircuit(c);
      fill(c, inner);
      inner = c;
    }
    return inner;
  }

  /** Builds input pin -> (inner subcircuit or NOT gate) -> output pin; returns {in, out}. */
  private static Component[] fill(Circuit c, Circuit inner) {
    final Component mid;
    if (inner == null) {
      final var not = ((AddTool) new GatesLibrary().getTool("NOT Gate")).getFactory();
      mid = not.createComponent(Location.create(200, 100, true), not.createAttributeSet());
    } else {
      mid = inner.getSubcircuitFactory().createComponent(Location.create(200, 100, true),
          inner.getSubcircuitFactory().createAttributeSet());
    }
    add(c, mid);
    final var inPin = Pin.FACTORY.createComponent(end(mid, false), Pin.FACTORY.createAttributeSet());
    final var outAttrs = Pin.FACTORY.createAttributeSet();
    outAttrs.setValue(Pin.ATTR_TYPE, Pin.OUTPUT);
    final var outPin = Pin.FACTORY.createComponent(end(mid, true), outAttrs);
    add(c, inPin);
    add(c, outPin);
    return new Component[] {inPin, outPin};
  }

  private static Location end(Component comp, boolean output) {
    return comp.getEnds().stream()
        .filter(e -> e.isOutput() == output)
        .map(EndData::getLocation)
        .findFirst()
        .orElseThrow();
  }

  private static void add(Circuit circuit, Component component) {
    final var mutation = new CircuitMutation(circuit);
    mutation.add(component);
    mutation.execute();
  }
}
