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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.SelectionArrange.Mode;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.arith.Adder;
import com.cburch.logisim.std.wiring.Pin;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SelectionArrangeTest {

  private final Project project;
  private final Circuit circuit;
  private final Selection selection;

  SelectionArrangeTest() {
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

  private static Component pin(int x, int y) {
    return Pin.FACTORY.createComponent(Location.create(x, y, false),
        Pin.FACTORY.createAttributeSet());
  }

  private static final Adder ADDER = new Adder();

  private static Component gate(int x, int y) {
    return ADDER.createComponent(Location.create(x, y, false), ADDER.createAttributeSet());
  }

  /** Bounds of {@code comp} once it sits at its target (or where it is when it does not move). */
  private static Bounds boundsAfter(Component comp, Map<Component, Location> targets) {
    final var target = targets.getOrDefault(comp, comp.getLocation());
    return comp.getBounds().translate(
        target.getX() - comp.getLocation().getX(), target.getY() - comp.getLocation().getY());
  }

  private static void assertOnGridAndNotNegative(Map<Component, Location> targets) {
    for (final var entry : targets.entrySet()) {
      final var loc = entry.getValue();
      assertEquals(0, loc.getX() % 10, "x on grid: " + loc);
      assertEquals(0, loc.getY() % 10, "y on grid: " + loc);
      assertTrue(loc.getX() >= 0 && loc.getY() >= 0, "not negative: " + loc);
      final var bds = boundsAfter(entry.getKey(), targets);
      assertTrue(bds.getX() >= 0 && bds.getY() >= 0, "bounds not negative: " + bds);
    }
  }

  @Test
  void alignLeftLinesUpTheLeftEdges() {
    final var a = pin(100, 50);
    final var b = pin(170, 100);
    final var c = gate(300, 160);
    final var comps = List.of(a, b, c);

    final var targets = SelectionArrange.computeTargets(comps, Mode.ALIGN_LEFT);

    assertOnGridAndNotNegative(targets);
    final var left = a.getBounds().getX();
    for (final var comp : comps) {
      assertEquals(left, boundsAfter(comp, targets).getX());
      assertEquals(comp.getLocation().getY(),
          targets.getOrDefault(comp, comp.getLocation()).getY(), "align left keeps y");
    }
    assertFalse(targets.containsKey(a), "the leftmost component stays put");
  }

  @Test
  void alignRightAndBottomUseTheFarEdges() {
    final var small = pin(100, 50);
    final var wide = gate(300, 200);
    final var comps = List.of(small, wide);

    final var right = SelectionArrange.computeTargets(comps, Mode.ALIGN_RIGHT);
    final var edge = wide.getBounds().getX() + wide.getBounds().getWidth();
    final var smallBds = boundsAfter(small, right);
    assertEquals(edge, smallBds.getX() + smallBds.getWidth());

    final var bottom = SelectionArrange.computeTargets(comps, Mode.ALIGN_BOTTOM);
    final var lowest = wide.getBounds().getY() + wide.getBounds().getHeight();
    final var smallBottom = boundsAfter(small, bottom);
    assertEquals(lowest, smallBottom.getY() + smallBottom.getHeight());
    assertOnGridAndNotNegative(bottom);
  }

  @Test
  void alignCenterAndMiddleCentreOnTheSelectionBoxToTheNearestGridPoint() {
    final var a = pin(100, 60);
    final var b = pin(200, 160);
    final var c = pin(300, 300);
    final var comps = List.of(a, b, c);

    final var middle = SelectionArrange.computeTargets(comps, Mode.ALIGN_MIDDLE);
    assertOnGridAndNotNegative(middle);
    final var centres = new HashSet<Integer>();
    for (final var comp : comps) centres.add(boundsAfter(comp, middle).getCenterY());
    assertEquals(1, centres.size(), "same-size components end up on one line");
    final var boxCentre = (a.getBounds().getY() + c.getBounds().getY()
        + c.getBounds().getHeight()) / 2;
    assertTrue(Math.abs(centres.iterator().next() - boxCentre) <= 5);

    final var center = SelectionArrange.computeTargets(comps, Mode.ALIGN_CENTER);
    for (final var comp : comps) {
      assertEquals(200, targetsOrSelf(center, comp).getX(), "all move to the middle column");
    }
  }

  private static Location targetsOrSelf(Map<Component, Location> targets, Component comp) {
    return targets.getOrDefault(comp, comp.getLocation());
  }

  @Test
  void distributeKeepsTheOuterComponentsAndEvensOutTheGaps() {
    final var first = pin(100, 100);
    final var second = pin(130, 100);
    final var third = pin(150, 100);
    final var last = pin(400, 100);
    final var comps = List.of(third, last, first, second);

    final var targets = SelectionArrange.computeTargets(comps, Mode.DISTRIBUTE_HORIZONTAL);

    assertOnGridAndNotNegative(targets);
    assertFalse(targets.containsKey(first));
    assertFalse(targets.containsKey(last));
    assertEquals(200, targetsOrSelf(targets, second).getX());
    assertEquals(300, targetsOrSelf(targets, third).getX());
    for (final var comp : comps) {
      assertEquals(100, targetsOrSelf(targets, comp).getY(), "horizontal distribution keeps y");
    }
  }

  @Test
  void distributeVerticallyHandlesDifferentSizes() {
    final var top = pin(100, 50);
    final var big = gate(200, 90);
    final var bottom = pin(100, 350);
    final var comps = List.of(top, big, bottom);

    final var targets = SelectionArrange.computeTargets(comps, Mode.DISTRIBUTE_VERTICAL);

    final var topBds = boundsAfter(top, targets);
    final var bigBds = boundsAfter(big, targets);
    final var bottomBds = boundsAfter(bottom, targets);
    final var gap1 = bigBds.getY() - (topBds.getY() + topBds.getHeight());
    final var gap2 = bottomBds.getY() - (bigBds.getY() + bigBds.getHeight());
    assertTrue(Math.abs(gap1 - gap2) <= 10, "gaps " + gap1 + " and " + gap2);
    assertOnGridAndNotNegative(targets);
  }

  @Test
  void tooFewComponentsDoNothingAndWiresDoNotCount() {
    final var wire = Wire.create(Location.create(10, 10, false), Location.create(50, 10, false));
    assertTrue(SelectionArrange.computeTargets(List.of(pin(100, 50), wire), Mode.ALIGN_LEFT)
        .isEmpty());
    assertTrue(SelectionArrange.computeTargets(
        List.of(pin(100, 50), pin(200, 90), wire), Mode.DISTRIBUTE_HORIZONTAL).isEmpty());

    selection.add(pin(100, 50));
    selection.add(wire);
    assertFalse(SelectionArrange.isApplicable(selection, Mode.ALIGN_TOP));
    selection.add(pin(200, 100));
    assertTrue(SelectionArrange.isApplicable(selection, Mode.ALIGN_TOP));
    assertFalse(SelectionArrange.isApplicable(selection, Mode.DISTRIBUTE_VERTICAL));
  }

  @Test
  void neverMovesAnythingToNegativeCoordinates() {
    // A box reaching 5 left of zero may stay there but must not be pushed further out.
    assertEquals(10, SelectionArrange.place(10, -5, -30.0, true));
    // Aligning would put the box 3 units below zero: it stops at the last grid point before.
    assertEquals(20, SelectionArrange.place(20, 5, -23.0, true));
    assertEquals(10, SelectionArrange.place(40, 30, -33.0, true));
    assertEquals(0, SelectionArrange.place(30, 30, -50.0, true));
  }

  @Test
  void stackingComponentsOnTopOfEachOtherIsAConflict() {
    final var a = pin(100, 50);
    final var b = pin(100, 150);
    final var comps = List.of(a, b);
    final var targets = SelectionArrange.computeTargets(comps, Mode.ALIGN_TOP);
    assertEquals(1, targets.size());
    assertTrue(SelectionArrange.hasConflict(circuit, comps, targets));

    final var c = pin(200, 150);
    final var fine = SelectionArrange.computeTargets(List.of(a, c), Mode.ALIGN_TOP);
    assertFalse(SelectionArrange.hasConflict(circuit, List.of(a, c), fine));
  }

  @Test
  void alignIsOneUndoableStepAndWiresFollow() {
    final var upper = gate(300, 100);
    final var lower = gate(200, 250);
    final var input = lower.getEnds().get(0).getLocation();
    final var source = pin(input.getX() - 60, input.getY());
    final var wire = Wire.create(source.getLocation(), input);
    final var xn = new CircuitMutation(circuit);
    xn.add(upper);
    xn.add(lower);
    xn.add(source);
    xn.add(wire);
    xn.execute();
    final var before = new HashSet<>(circuit.getNonWires());
    final var wiresBefore = new HashSet<>(circuit.getWires());
    selection.add(upper);
    selection.add(lower);

    assertEquals(SelectionArrange.Outcome.OK,
        SelectionArrange.check(selection, circuit, Mode.ALIGN_RIGHT));
    project.doAction(SelectionArrange.createAction(selection, Mode.ALIGN_RIGHT, true));

    final var moved = findAt(Location.create(300, 250, false));
    assertTrue(moved != null, "the lower gate lines up under the upper one");
    assertTrue(circuit.getNonWires().contains(source), "the pin is not part of the move");
    assertTrue(selection.getComponents().contains(moved), "the selection follows the move");
    assertTrue(connected(source.getLocation(), moved.getEnds().get(0).getLocation()),
        "the wire follows the gate");
    final var after = new HashSet<>(circuit.getNonWires());

    project.undoAction();
    assertEquals(before, new HashSet<>(circuit.getNonWires()));
    assertEquals(wiresBefore, new HashSet<>(circuit.getWires()));

    project.redoAction();
    assertEquals(after, new HashSet<>(circuit.getNonWires()));
    assertNotEquals(wiresBefore, new HashSet<>(circuit.getWires()));
  }

  @Test
  void everyArrangeMenuItemRunsItsOwnCommandAndHasALabel() {
    final var modes = new HashSet<Mode>();
    for (final var item : LogisimMenuBar.ARRANGE_ITEMS) {
      modes.add(LayoutEditHandler.arrangeMode(item));
      final var label = LogisimMenuBar.arrangeItemText(item);
      assertFalse(label.isBlank() || label.startsWith("edit"), "label for " + item + ": " + label);
    }
    assertEquals(Mode.values().length, modes.size());
  }

  private Component findAt(Location loc) {
    for (final var comp : circuit.getNonWires()) {
      if (comp.getFactory() == ADDER && comp.getLocation().equals(loc)) return comp;
    }
    return null;
  }

  /** Whether wires join {@code from} and {@code to}. */
  private boolean connected(Location from, Location to) {
    final var seen = new HashSet<Location>();
    final var todo = new ArrayDeque<Location>();
    todo.add(from);
    while (!todo.isEmpty()) {
      final var loc = todo.poll();
      if (loc.equals(to)) return true;
      if (!seen.add(loc)) continue;
      final var next = new ArrayList<Location>();
      for (final var w : circuit.getWires()) {
        if (w.getEnd0().equals(loc)) next.add(w.getEnd1());
        else if (w.getEnd1().equals(loc)) next.add(w.getEnd0());
      }
      todo.addAll(next);
    }
    return false;
  }
}
