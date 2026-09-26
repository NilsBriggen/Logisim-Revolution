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

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.fpga.designrulecheck.CorrectLabel;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.util.SyntaxChecker;

/**
 * The one set of rules for circuit names, shared by New Circuit and by renaming a circuit, so that
 * both accept the same names and explain a refusal in the same words.
 */
public final class CircuitNameValidator {
  private CircuitNameValidator() {}

  /**
   * Why {@code name} cannot name a circuit of {@code file}, or {@code null} if it can.
   *
   * @param circuit the circuit being renamed, or {@code null} for a new circuit
   */
  public static String problemWith(LogisimFile file, String name, Circuit circuit) {
    if (name == null || name.isEmpty()) return S.get("circuitNameEmptyError");
    final var hdlType = AppPreferences.HdlType.get();
    if (CorrectLabel.isKeyword(name, hdlType, false)) {
      return S.get("circuitNameKeywordError", name);
    }
    if (SyntaxChecker.getErrorMessage(name, hdlType) != null) {
      return S.get("circuitNameSyntaxError", name);
    }
    if (file != null && isNameInUse(file, name, circuit)) {
      return S.get("circuitNameInUseError", name);
    }
    if (circuit != null) {
      for (final var component : circuit.getNonWires()) {
        if (!(component.getFactory() instanceof Pin)) continue;
        final var label = component.getAttributeSet().getValue(StdAttr.LABEL);
        if (!label.isEmpty() && SyntaxChecker.namesEqual(label, name, hdlType)) {
          return S.get("circuitNamePinLabelError", name);
        }
      }
    }
    return null;
  }

  private static boolean isNameInUse(LogisimFile file, String name, Circuit circuit) {
    for (final var lib : file.getLibraries()) {
      if (isNameInLibrary(lib, name)) return true;
    }
    for (final var tool : file.getTools()) {
      if (tool.getFactory() instanceof SubcircuitFactory factory
          && factory.getSubcircuit() == circuit) {
        continue;
      }
      if (SyntaxChecker.namesEqualForCurrentHdl(name, tool.getName())) return true;
    }
    return false;
  }

  private static boolean isNameInLibrary(Library lib, String name) {
    for (final var sub : lib.getLibraries()) {
      if (isNameInLibrary(sub, name)) return true;
    }
    for (final var tool : lib.getTools()) {
      if (SyntaxChecker.namesEqualForCurrentHdl(name, tool.getName())) return true;
    }
    return false;
  }
}
