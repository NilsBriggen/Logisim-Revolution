/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.gates.GateAttributes;
import com.cburch.logisim.std.gates.GatesLibrary;
import com.cburch.logisim.tools.AddTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ReplaceComponentTest {

  private final Project project;
  private final Circuit circuit;

  ReplaceComponentTest() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    file.stopAutosaveThread(false);
    project = new Project(file);
    circuit = file.getMainCircuit();
    circuit.setProject(project);
    project.setCurrentCircuit(circuit);
  }

  @AfterEach
  void shutDown() {
    project.getSimulator().shutDown();
  }

  private static ComponentFactory gate(String name) {
    return ((AddTool) new GatesLibrary().getTool(name)).getFactory();
  }

  @Test
  void keepsSharedAttributesAndConnectionsAndUndoes() {
    final var and = gate("AND Gate");
    final var attrs = and.createAttributeSet();
    attrs.setValue(StdAttr.LABEL, "g1");
    attrs.setValue(StdAttr.FACING, Direction.NORTH);
    attrs.setValue(GateAttributes.ATTR_INPUTS, 3);
    final var old = and.createComponent(Location.create(200, 200, false), attrs);
    final var input = old.getEnds().get(1).getLocation();
    final var wire = Wire.create(input, input.translate(0, 40));
    final var xn = new CircuitMutation(circuit);
    xn.add(old);
    xn.add(wire);
    xn.execute();

    final var or = ReplaceComponent.build(old, gate("OR Gate"));
    assertEquals("g1", or.getAttributeSet().getValue(StdAttr.LABEL));
    assertEquals(Direction.NORTH, or.getAttributeSet().getValue(StdAttr.FACING));
    assertEquals(3, or.getAttributeSet().getValue(GateAttributes.ATTR_INPUTS));
    assertTrue(ReplaceComponent.lostConnections(circuit, old, or).isEmpty());

    project.doAction(ReplaceComponent.action(circuit, old, or));
    assertTrue(circuit.getNonWires().contains(or));
    assertFalse(circuit.getNonWires().contains(old));
    project.undoAction();
    assertTrue(circuit.getNonWires().contains(old));
    assertTrue(circuit.getWires().contains(wire));
  }

  @Test
  void reportsConnectionsTheNewKindCannotKeep() {
    final var and = gate("AND Gate");
    final var old = and.createComponent(Location.create(200, 200, false), and.createAttributeSet());
    final var input = old.getEnds().get(1).getLocation();
    final var xn = new CircuitMutation(circuit);
    xn.add(old);
    xn.add(Wire.create(input, input.translate(-40, 0)));
    xn.execute();

    final var not = ReplaceComponent.build(old, gate("NOT Gate"));
    assertEquals(1, ReplaceComponent.lostConnections(circuit, old, not).size());
  }

  @Test
  void candidatesLeaveOutTheComponentsOwnKindAndTheCurrentCircuit() {
    project.getLogisimFile().addLibrary(new GatesLibrary());
    final var and = gate("AND Gate");
    final var old = and.createComponent(Location.create(200, 200, false), and.createAttributeSet());
    final var names = ReplaceComponent.candidates(project, old).stream()
        .map(c -> c.tool().getFactory())
        .toList();
    assertFalse(names.isEmpty());
    assertFalse(names.stream().anyMatch(f -> f.getName().equals(old.getFactory().getName())
        && f.getClass() == old.getFactory().getClass()));
    assertFalse(names.contains(circuit.getSubcircuitFactory()));
  }
}
