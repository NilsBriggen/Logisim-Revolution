/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Strings;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.circuit.WireInfo;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.gui.generic.AttrTableModelRow;
import java.awt.Window;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Read-only Width and Value rows for the inspector when a wire, or several wires of one net, is
 * selected.
 *
 * <p>A wire's attributes are its direction and length, which say nothing about what it carries.
 * The Poke tool showed the value only while the mouse was held down, and the width was only ever
 * hinted at by the wire's colour.
 */
final class WireInfoRows {

  private WireInfoRows() {}

  /**
   * The rows for a selection, or none when it is not made only of wires on a single net.
   *
   * @param state supplies the state to read values from when a row is drawn, so they stay live
   */
  static List<AttrTableModelRow> forSelection(
      Circuit circuit, Supplier<CircuitState> state, Collection<Component> selection) {
    if (circuit == null || selection.isEmpty()) return List.of();
    Wire first = null;
    for (final var component : selection) {
      if (!(component instanceof Wire wire)) return List.of();
      if (first == null) {
        first = wire;
      } else if (!circuit.getWireSet(first).containsWire(wire)) {
        return List.of();
      }
    }
    final var wire = first;
    return List.of(
        new InfoRow(
            Strings.S.get("wireInfoWidth"), () -> WireInfo.widthText(circuit, wire)),
        new InfoRow(
            Strings.S.get("wireInfoValue"),
            () -> WireInfo.valueText(circuit, state.get(), wire)));
  }

  /** A row with a label and a live value that cannot be edited. */
  record InfoRow(String label, Supplier<String> value) implements AttrTableModelRow {
    @Override
    public java.awt.Component getEditor(Window parent) {
      return null;
    }

    @Override
    public String getLabel() {
      return label;
    }

    @Override
    public String getValue() {
      try {
        return value.get();
      } catch (RuntimeException ex) {
        // The circuit can change under a row that is being repainted; show nothing rather than
        // break the table.
        return "";
      }
    }

    @Override
    public boolean isValueEditable() {
      return false;
    }

    @Override
    public boolean multiEditCompatible(AttrTableModelRow other) {
      return false;
    }

    @Override
    public void setValue(Window parent, Object value) {
      // Read-only.
    }
  }
}
