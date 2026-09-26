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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.Tunnel;
import com.cburch.logisim.tools.ToolTipMaker;
import java.util.List;
import org.junit.jupiter.api.Test;

class WireInfoTest {

  private static Circuit newCircuit() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    final var circuit = file.getMainCircuit();
    circuit.setProject(project);
    return circuit;
  }

  static Component pin(int x, int y, int width) {
    final var attrs = Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.WIDTH, BitWidth.create(width));
    return Pin.FACTORY.createComponent(Location.create(x, y, false), attrs);
  }

  static Component tunnel(int x, int y, int width, String label) {
    final var attrs = Tunnel.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.WIDTH, BitWidth.create(width));
    attrs.setValue(StdAttr.LABEL, label);
    return Tunnel.FACTORY.createComponent(Location.create(x, y, false), attrs);
  }

  static void add(Circuit circuit, Component... components) {
    final var mutation = new CircuitMutation(circuit);
    for (final var component : components) mutation.add(component);
    mutation.execute();
  }

  private static Wire wire(int x0, int y0, int x1, int y1) {
    return Wire.create(Location.create(x0, y0, false), Location.create(x1, y1, false));
  }

  @Test
  void wireBetweenDifferentWidthsNamesBothWidths() {
    final var circuit = newCircuit();
    final var wire = wire(100, 100, 200, 100);
    add(circuit, pin(100, 100, 4), pin(200, 100, 8), wire);

    assertEquals(List.of(4, 8), WireInfo.conflictingWidths(circuit, wire));
    assertEquals(BitWidth.UNKNOWN, WireInfo.width(circuit, wire));
    assertEquals("Conflict: 4 ≠ 8 bits", WireInfo.widthText(circuit, wire));
    assertTrue(WireInfo.toolTip(circuit, null, wire).contains("4 ≠ 8"));
  }

  @Test
  void wireBetweenMatchingWidthsHasWidthAndNoConflict() {
    final var circuit = newCircuit();
    final var wire = wire(100, 100, 200, 100);
    add(circuit, pin(100, 100, 8), pin(200, 100, 8), wire);

    assertEquals(List.of(), WireInfo.conflictingWidths(circuit, wire));
    assertEquals("8 bits", WireInfo.widthText(circuit, wire));
    assertEquals("1 bit", WireInfo.bitsText(BitWidth.ONE));
  }

  @Test
  void conflictThroughTunnelsIsReportedOnEitherSide() {
    final var circuit = newCircuit();
    final var left = wire(100, 100, 200, 100);
    final var right = wire(400, 100, 500, 100);
    add(
        circuit,
        pin(100, 100, 8),
        tunnel(200, 100, 8, "data"),
        tunnel(400, 100, 4, "data"),
        pin(500, 100, 4),
        left,
        right);

    assertEquals(List.of(4, 8), WireInfo.conflictingWidths(circuit, left));
    assertEquals(List.of(4, 8), WireInfo.conflictingWidths(circuit, right));
  }

  @Test
  void wiresOfferATooltip() {
    final var wire = wire(100, 100, 200, 100);
    assertTrue(wire.getFeature(ToolTipMaker.class) instanceof ToolTipMaker);
  }
}
