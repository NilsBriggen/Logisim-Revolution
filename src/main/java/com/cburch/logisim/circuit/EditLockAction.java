/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit;

import static com.cburch.logisim.circuit.Strings.S;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Locks or unlocks a circuit, or some of its components, as one undoable step.
 *
 * <p>It is a modification like any other, so the project becomes dirty and the lock is saved with
 * it. Locking is always allowed, whatever is locked already: it is how the lock is taken off.
 */
public final class EditLockAction extends Action {

  /** What the action changes. */
  enum Kind {
    CIRCUIT,
    COMPONENTS
  }

  private final Circuit circuit;
  private final Kind kind;
  private final boolean locked;
  /** The components whose lock this action changes; only those it actually changes. */
  private final List<Component> components;

  private EditLockAction(Circuit circuit, Kind kind, boolean locked, List<Component> components) {
    this.circuit = circuit;
    this.kind = kind;
    this.locked = locked;
    this.components = components;
  }

  /** Locks or unlocks the whole circuit, or returns {@code null} when it already is. */
  public static Action setCircuitLocked(Circuit circuit, boolean locked) {
    if (circuit == null || circuit.isEditLocked() == locked) return null;
    return new EditLockAction(circuit, Kind.CIRCUIT, locked, List.of());
  }

  /**
   * Locks or unlocks {@code components} of {@code circuit}. Wires, and components already in the
   * requested state, are left alone; returns {@code null} when that leaves nothing to do.
   */
  public static Action setComponentsLocked(
      Circuit circuit, Collection<? extends Component> components, boolean locked) {
    if (circuit == null || components == null) return null;
    final var changing = new ArrayList<Component>();
    for (final var comp : components) {
      if (comp instanceof Wire || !circuit.contains(comp)) continue;
      if (circuit.isComponentEditLocked(comp) != locked) changing.add(comp);
    }
    if (changing.isEmpty()) return null;
    return new EditLockAction(circuit, Kind.COMPONENTS, locked, List.copyOf(changing));
  }

  /** Whether every lockable component of {@code components} is locked, and there is one. */
  public static boolean allLocked(Circuit circuit, Collection<? extends Component> components) {
    var any = false;
    for (final var comp : components) {
      if (comp instanceof Wire) continue;
      if (!circuit.isComponentEditLocked(comp)) return false;
      any = true;
    }
    return any;
  }

  @Override
  public void doIt(Project proj) {
    apply(locked);
  }

  @Override
  public void undo(Project proj) {
    apply(!locked);
  }

  private void apply(boolean value) {
    if (kind == Kind.CIRCUIT) {
      circuit.setEditLocked(value);
    } else {
      circuit.setComponentsEditLocked(components, value);
    }
  }

  @Override
  public String getName() {
    if (kind == Kind.CIRCUIT) {
      return S.get(locked ? "lockCircuitAction" : "unlockCircuitAction");
    }
    return S.get(locked ? "lockComponentsAction" : "unlockComponentsAction");
  }
}
