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
import com.cburch.logisim.instance.StdAttr;

/**
 * Refuses an edit that would change a locked circuit or a locked component.
 *
 * <p>Thrown before anything is changed, so the edit simply does not happen. {@link
 * com.cburch.logisim.proj.Project#doAction} catches it, leaves the undo history as it was and
 * reports the message in the status bar.
 *
 * <p>A lock guards against accidental edits only. It is not a security feature: anyone can unlock.
 */
public class EditLockedException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final transient Circuit circuit;
  private final transient Component component;

  /**
   * @param circuit the circuit the edit was aimed at
   * @param component the locked component, or {@code null} when the whole circuit is locked
   */
  public EditLockedException(Circuit circuit, Component component) {
    super(null, null, false, false);
    this.circuit = circuit;
    this.component = component;
  }

  public Circuit getCircuit() {
    return circuit;
  }

  /** The locked component the edit touched, or {@code null} when the circuit itself is locked. */
  public Component getComponent() {
    return component;
  }

  @Override
  public String getMessage() {
    if (component == null) {
      return S.get("editLockedCircuitRefused", circuit == null ? "" : circuit.getName());
    }
    return S.get("editLockedComponentRefused", describe(component));
  }

  /** The component's kind, and its label when it has one: {@code AND Gate "carry"}. */
  public static String describe(Component component) {
    final var name = component.getFactory().getDisplayName();
    final var attrs = component.getAttributeSet();
    final var label = attrs.containsAttribute(StdAttr.LABEL) ? attrs.getValue(StdAttr.LABEL) : null;
    return label == null || label.isBlank() ? name : name + " \"" + label + "\"";
  }
}
