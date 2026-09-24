/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import com.cburch.logisim.circuit.CircuitState;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Circuit-owned histories; an HDL editor has no active simulation history. */
final class LogModelHistory {
  private final Map<CircuitState, Model> models = new HashMap<>();
  private final Function<CircuitState, Model> factory;
  private volatile Model current;

  LogModelHistory() {
    this(Model::new);
  }

  LogModelHistory(Function<CircuitState, Model> factory) {
    this.factory = factory;
  }

  Model current() {
    return current;
  }

  boolean select(CircuitState state) {
    final var old = current;
    if (state == (old == null ? null : old.getCircuitState())) return false;
    // Publish inactivity before cancelling the logger or constructing another model.
    current = null;
    if (old != null) old.setSelected(false);
    if (state != null) {
      final var next = models.computeIfAbsent(state, factory);
      next.setSelected(true);
      current = next;
    }
    return true;
  }
}
