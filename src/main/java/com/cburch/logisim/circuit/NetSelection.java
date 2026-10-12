/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Tunnel;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Finds the net a wire belongs to within one circuit: every wire connected to it, directly or
 * through tunnels with the same label, together with those tunnels.
 */
public final class NetSelection {

  private NetSelection() {}

  /** Returns the wires and tunnels of the net {@code start} is on, {@code start} included. */
  public static Set<Component> netOf(Circuit circuit, Wire start) {
    final var net = new LinkedHashSet<Component>();
    final var labels = new LinkedHashSet<String>();
    final var todo = new ArrayDeque<Wire>();
    todo.add(start);
    while (!todo.isEmpty()) {
      final var wire = todo.poll();
      if (net.contains(wire)) continue;
      final var wireSet = circuit.getWireSet(wire);
      final var points = new LinkedHashSet<Location>();
      for (final var w : circuit.getWires()) {
        if (wireSet.containsWire(w) && net.add(w)) {
          points.add(w.getEnd0());
          points.add(w.getEnd1());
        }
      }
      for (final var point : points) {
        for (final var comp : circuit.getNonWires(point)) {
          final var label = tunnelLabel(comp);
          if (label != null && net.add(comp)) labels.add(label);
        }
      }
      // Same-named tunnels anywhere in the circuit join the net, and so do their wires.
      for (final var comp : circuit.getNonWires()) {
        final var label = tunnelLabel(comp);
        if (label == null || !labels.contains(label) || !net.add(comp)) continue;
        for (final var end : comp.getEnds()) {
          for (final var w : circuit.getWires(end.getLocation())) {
            if (!net.contains(w)) todo.add(w);
          }
        }
      }
    }
    return net;
  }

  /** The label a tunnel connects by, trimmed as the wiring does, or null for anything else. */
  private static String tunnelLabel(Component comp) {
    if (!(comp.getFactory() instanceof Tunnel)) return null;
    final var label = comp.getAttributeSet().getValue(StdAttr.LABEL);
    return label == null || label.isBlank() ? null : label.trim();
  }
}
