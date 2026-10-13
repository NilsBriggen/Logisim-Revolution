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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.SplitterFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.SelectionTransform.Mode;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.gates.GatesLibrary;
import com.cburch.logisim.std.plexers.Multiplexer;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.AddTool;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SelectionTransformTest {

  private final Project project;
  private final Circuit circuit;
  private final Selection selection;

  SelectionTransformTest() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    file.stopAutosaveThread(false);
    project = new Project(file);
    circuit = file.getMainCircuit();
    circuit.setProject(project);
    project.setCurrentCircuit(circuit);
    final var canvas = mock(Canvas.class);
    when(canvas.getProject()).thenReturn(project);
    when(canvas.getCircuit()).thenReturn(circuit);
    selection = new Selection(project, canvas);
    when(canvas.getSelection()).thenReturn(selection);
  }

  @AfterEach
  void shutDown() {
    project.getSimulator().shutDown();
  }

  private static Component gate(String name, int x, int y) {
    final var factory = ((AddTool) new GatesLibrary().getTool(name)).getFactory();
    return factory.createComponent(Location.create(x, y, false), factory.createAttributeSet());
  }

  private static Component pin(int x, int y, Direction facing) {
    final var attrs = Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.FACING, facing);
    return Pin.FACTORY.createComponent(Location.create(x, y, false), attrs);
  }

  /** A pin wired into an AND gate, plus a splitter and a multiplexer. */
  private List<Component> sample() {
    final var and = gate("AND Gate", 200, 100);
    final var in = and.getEnds().get(1).getLocation();
    final var source = pin(in.getX() - 40, in.getY(), Direction.EAST);
    final var wire = Wire.create(source.getLocation(), in);
    final var splitter =
        SplitterFactory.instance.createComponent(
            Location.create(260, 200, false), SplitterFactory.instance.createAttributeSet());
    final var muxFactory = new Multiplexer();
    final var mux =
        muxFactory.createComponent(
            Location.create(120, 220, false), muxFactory.createAttributeSet());
    return List.of(and, source, wire, splitter, mux);
  }

  private static Set<Object> describe(java.util.Collection<Component> comps) {
    final var ret = new HashSet<Object>();
    for (final var comp : comps) {
      ret.add(
          List.of(
              comp.getFactory().getName(),
              comp.getLocation(),
              comp instanceof Wire w ? w.getEnd1() : comp.getAttributeSet().getValue(StdAttr.FACING) == null ? "" : comp.getAttributeSet().getValue(StdAttr.FACING)));
    }
    return ret;
  }

  private static List<Location> endLocations(java.util.Collection<Component> comps) {
    final var ret = new ArrayList<Location>();
    for (final var comp : comps) {
      for (final var end : comp.getEnds()) ret.add(end.getLocation());
    }
    ret.sort(null);
    return ret;
  }

  private static List<Component> apply(List<Component> comps, Mode mode, int times) {
    var current = comps;
    for (var i = 0; i < times; i++) {
      // A fixed pivot, so repeated transforms compose exactly.
      current =
          new ArrayList<>(
              SelectionTransform.computeReplacements(current, mode, Location.create(200, 150, false))
                  .values());
    }
    return current;
  }

  @Test
  void connectionsLandWhereTheTransformPutsThem() {
    final var comps = sample();
    final var pivot = Location.create(200, 150, false);
    for (final var mode : Mode.values()) {
      final var expected = new ArrayList<Location>();
      for (final var loc : endLocations(comps)) {
        expected.add(mode.apply(loc, pivot.getX(), pivot.getY()));
      }
      expected.sort(null);
      final var after = SelectionTransform.computeReplacements(comps, mode, pivot).values();
      assertEquals(expected, endLocations(after), mode.toString());
      for (final var comp : after) {
        assertEquals(0, comp.getLocation().getX() % 10, comp + " on grid");
        assertEquals(0, comp.getLocation().getY() % 10, comp + " on grid");
      }
    }
  }

  @Test
  void fourQuarterTurnsAndTwoFlipsChangeNothing() {
    final var comps = sample();
    assertEquals(describe(comps), describe(apply(comps, Mode.ROTATE_CW, 4)));
    assertEquals(describe(comps), describe(apply(comps, Mode.ROTATE_CCW, 4)));
    assertEquals(describe(comps), describe(apply(comps, Mode.FLIP_HORIZONTAL, 2)));
    assertEquals(describe(comps), describe(apply(comps, Mode.FLIP_VERTICAL, 2)));
    assertEquals(
        describe(apply(comps, Mode.ROTATE_CW, 1)), describe(apply(comps, Mode.ROTATE_CCW, 3)));
  }

  @Test
  void rotatingTheSelectionIsOneUndoableActionAndKeepsItSelected() {
    final var comps = sample();
    final var xn = new CircuitMutation(circuit);
    for (final var comp : comps) xn.add(comp);
    xn.execute();
    final var before = describe(new ArrayList<>(circuit.getNonWires()));
    final var wiresBefore = new HashSet<>(circuit.getWires());
    for (final var comp : comps) selection.add(comp);

    project.doAction(SelectionTransform.createAction(selection, Mode.ROTATE_CW));
    assertEquals(comps.size(), selection.getComponents().size(), "the selection follows");
    assertTrue(!before.equals(describe(new ArrayList<>(circuit.getNonWires()))));
    // The wire still meets the gate input it was drawn to.
    final var wire = circuit.getWires().iterator().next();
    var meets = false;
    for (final var comp : circuit.getNonWires()) {
      for (final var end : comp.getEnds()) {
        meets |= end.getLocation().equals(wire.getEnd0()) || end.getLocation().equals(wire.getEnd1());
      }
    }
    assertTrue(meets);

    project.undoAction();
    assertEquals(before, describe(new ArrayList<>(circuit.getNonWires())));
    assertEquals(wiresBefore, new HashSet<>(circuit.getWires()));
  }
}
