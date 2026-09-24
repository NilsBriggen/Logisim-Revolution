/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import java.awt.Component;
import java.util.Objects;
import javax.swing.JComboBox;

/**
 * Whether several selected rows can be given a value together.
 *
 * <p>Selecting a run of rows and setting them all at once only ever worked for a splitter's bit
 * rows, because the rule asked whether both rows were that one particular kind of attribute.
 * Everything else — eight pins' data widths, four gates' facing — had to be done one row at a
 * time even though the rows offer exactly the same choices.
 *
 * <p>The rule here is the general one: two rows can be set together when they offer the same
 * choices. It is expressed over the editors the rows produce, which is what the table already has
 * in its hand at the moment it has to decide.
 */
public final class AttrMultiEdit {
  private AttrMultiEdit() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /**
   * Whether two rows offer the same choices.
   *
   * <p>Compare actual values: identical translated labels do not make unrelated option types
   * interchangeable. Splitter bit rows have their own model compatibility rule.
   */
  public static boolean sameChoices(Component first, Component second) {
    if (!(first instanceof JComboBox<?> a) || !(second instanceof JComboBox<?> b)) return false;
    if (a.isEditable() || b.isEditable()) return false;
    if (a.getItemCount() != b.getItemCount()) return false;
    for (var i = 0; i < a.getItemCount(); i++) {
      if (!Objects.equals(a.getItemAt(i), b.getItemAt(i))) return false;
    }
    return true;
  }
}
