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

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.WidthIncompatibilityData;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.Tunnel;
import com.cburch.logisim.tools.WiringTool;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The problems pill's logic, and the other width-error aids that share its fixtures. */
class CircuitProblemsTest {

  private static Circuit newCircuit() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    final var circuit = file.getMainCircuit();
    circuit.setProject(project);
    return circuit;
  }

  private static Component pin(int x, int y, int width) {
    final var attrs = Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.WIDTH, BitWidth.create(width));
    return Pin.FACTORY.createComponent(Location.create(x, y, false), attrs);
  }

  private static Component tunnel(int x, int y, int width, String label) {
    final var attrs = Tunnel.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.WIDTH, BitWidth.create(width));
    attrs.setValue(StdAttr.LABEL, label);
    return Tunnel.FACTORY.createComponent(Location.create(x, y, false), attrs);
  }

  private static Wire wire(int x0, int y0, int x1, int y1) {
    return Wire.create(Location.create(x0, y0, false), Location.create(x1, y1, false));
  }

  private static void add(Circuit circuit, Component... components) {
    final var mutation = new CircuitMutation(circuit);
    for (final var component : components) mutation.add(component);
    mutation.execute();
  }

  private static WidthIncompatibilityData problem(int x, int y, int... widths) {
    final var data = new WidthIncompatibilityData();
    for (var i = 0; i < widths.length; i++) {
      data.add(Location.create(x + 10 * i, y, false), BitWidth.create(widths[i]));
    }
    return data;
  }

  @Test
  void problemsAreOrderedTopToBottomThenLeftToRight() {
    final var low = problem(10, 200, 4, 8);
    final var highRight = problem(300, 100, 2, 3);
    final var highLeft = problem(50, 100, 1, 2);
    assertEquals(
        List.of(highLeft, highRight, low),
        CircuitProblems.ordered(Set.of(low, highRight, highLeft)));
    assertEquals(List.of(), CircuitProblems.ordered(null));
  }

  @Test
  void steppingWrapsAroundAtBothEnds() {
    assertEquals(0, CircuitProblems.stepIndex(-1, 3, 1));
    assertEquals(2, CircuitProblems.stepIndex(-1, 3, -1));
    assertEquals(0, CircuitProblems.stepIndex(2, 3, 1));
    assertEquals(2, CircuitProblems.stepIndex(0, 3, -1));
    assertEquals(1, CircuitProblems.stepIndex(1, 3, 0));
    assertEquals(-1, CircuitProblems.stepIndex(0, 0, 1));
  }

  @Test
  void theNetOfAProblemIsItsWires() {
    final var circuit = newCircuit();
    final var first = wire(100, 100, 150, 100);
    final var second = wire(150, 100, 150, 200);
    add(circuit, pin(100, 100, 4), pin(150, 200, 8), first, second);

    final var problems = CircuitProblems.ordered(circuit.getWidthIncompatibilityData());
    assertEquals(1, problems.size());
    final var net = CircuitProblems.netOf(circuit, problems.get(0));
    assertEquals(Set.of(first, second), Set.copyOf(net));
  }

  @Test
  void badgesNameTheWidthsAnEndClashesWith() {
    final var data = new WidthIncompatibilityData();
    data.add(Location.create(10, 10, false), BitWidth.create(8));
    data.add(Location.create(90, 10, false), BitWidth.create(4));
    assertEquals("8≠4", CanvasPainter.widthCaption(data, 0));
    assertEquals("4≠8", CanvasPainter.widthCaption(data, 1));

    final var samePoint = new WidthIncompatibilityData();
    samePoint.add(Location.create(10, 10, false), BitWidth.create(8));
    samePoint.add(Location.create(10, 10, false), BitWidth.create(4));
    assertEquals("8/4", CanvasPainter.widthCaption(samePoint, 0));
  }

  @Test
  void selectingATunnelFindsTheTunnelsWithItsLabel() {
    final var circuit = newCircuit();
    final var selected = tunnel(200, 100, 8, "data");
    final var partner = tunnel(400, 100, 4, " data ");
    final var stranger = tunnel(400, 200, 4, "other");
    add(circuit, selected, partner, stranger, pin(100, 100, 8));

    assertEquals(List.of(partner), TunnelPartners.partnersOf(circuit, Set.of(selected), null));
    assertEquals(List.of(partner), TunnelPartners.partnersOf(circuit, Set.of(), selected));
    assertTrue(TunnelPartners.partnersOf(circuit, Set.of(), null).isEmpty());
  }

  @Test
  void theInspectorShowsWidthAndValueForTheWiresOfOneNet() {
    final var circuit = newCircuit();
    final var first = wire(100, 100, 150, 100);
    final var second = wire(150, 100, 150, 200);
    final var elsewhere = wire(100, 300, 200, 300);
    final var input = pin(100, 100, 4);
    add(circuit, input, pin(150, 200, 8), first, second, elsewhere);

    final var rows = WireInfoRows.forSelection(circuit, () -> null, List.of(first, second));
    assertEquals(2, rows.size());
    assertEquals("Width", rows.get(0).getLabel());
    assertEquals("Conflict: 4 \u2260 8 bits", rows.get(0).getValue());
    assertEquals("Value", rows.get(1).getLabel());
    assertTrue(rows.stream().noneMatch(row -> row.isValueEditable()));

    assertTrue(WireInfoRows.forSelection(circuit, () -> null, List.of(first, input)).isEmpty());
    assertTrue(WireInfoRows.forSelection(circuit, () -> null, List.of(first, elsewhere)).isEmpty());
  }

  @Test
  void drawingAWireBetweenDifferentWidthsIsFlagged() {
    final var circuit = newCircuit();
    add(circuit, pin(100, 100, 4), pin(200, 100, 8), pin(100, 200, 8));
    final var from = Location.create(100, 100, false);
    assertEquals(
        List.of(4, 8),
        WiringTool.widthConflict(circuit, from, Location.create(200, 100, false)));
    assertEquals(
        List.of(),
        WiringTool.widthConflict(
            circuit, Location.create(100, 200, false), Location.create(200, 100, false)));
    assertEquals(
        List.of(),
        WiringTool.widthConflict(circuit, from, Location.create(300, 300, false)));
  }
}
