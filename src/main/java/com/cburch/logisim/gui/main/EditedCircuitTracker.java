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
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.file.LogisimFile;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Finds out which circuits an edit changed, so the editor tabs can mark exactly those.
 *
 * <p>Marking "the current circuit" on every completed action put the unsaved dot on the wrong tab:
 * adding a circuit marked the circuit the user was on, not the new one, and an edit reached through
 * one circuit's tab could change another. This listens to every circuit of the file and remembers
 * which of them reported a modification between {@link #begin()} and {@link #finish(Circuit)}.
 */
final class EditedCircuitTracker implements CircuitListener {
  private final Set<Circuit> watched = Collections.newSetFromMap(new IdentityHashMap<>());
  private final Set<Circuit> touched = new LinkedHashSet<>();

  /** Starts watching every circuit of {@code file} (and forgets the circuits of a previous one). */
  void watch(LogisimFile file) {
    for (final var circuit : watched) circuit.removeCircuitListener(this);
    watched.clear();
    touched.clear();
    if (file == null) return;
    for (final var circuit : file.getCircuits()) watch(circuit);
  }

  /** Starts watching one circuit, for a circuit that was just added to the file. */
  void watch(Circuit circuit) {
    if (circuit != null && watched.add(circuit)) circuit.addCircuitListener(this);
  }

  /** Stops watching a circuit that was removed from the file. */
  void forget(Circuit circuit) {
    if (circuit != null && watched.remove(circuit)) circuit.removeCircuitListener(this);
    touched.remove(circuit);
  }

  /** Marks a circuit as changed by the running edit, e.g. one the edit has just created. */
  void touch(Circuit circuit) {
    if (circuit != null) touched.add(circuit);
  }

  /** Called when an edit starts. */
  void begin() {
    touched.clear();
  }

  /**
   * Called when an edit has finished: returns the circuits it changed, or {@code fallback} when no
   * circuit reported a change (for edits such as an appearance change that do not fire circuit
   * events).
   */
  Set<Circuit> finish(Circuit fallback) {
    final var ret = new LinkedHashSet<Circuit>(touched);
    touched.clear();
    if (ret.isEmpty() && fallback != null) ret.add(fallback);
    return ret;
  }

  @Override
  public void circuitChanged(CircuitEvent event) {
    // A transaction reports only the circuits it modified; component invalidations are left out
    // on purpose, since the simulator fires them too.
    switch (event.getAction()) {
      case CircuitEvent.TRANSACTION_DONE,
          CircuitEvent.ACTION_SET_NAME,
          CircuitEvent.ACTION_CLEAR,
          CircuitEvent.CHANGE_DEFAULT_BOX_APPEARANCE -> touch(event.getCircuit());
      default -> {
        // not an edit of the circuit's content
      }
    }
  }
}
