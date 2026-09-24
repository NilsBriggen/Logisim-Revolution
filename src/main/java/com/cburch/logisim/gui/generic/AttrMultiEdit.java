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
import java.util.ArrayList;
import java.util.List;
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
   * <p>Compared by what the user reads, not by object identity: two attributes may build equal but
   * separate option objects, and a splitter builds a fresh set per bit.
   */
  public static boolean sameChoices(Component first, Component second) {
    if (!(first instanceof JComboBox<?> a) || !(second instanceof JComboBox<?> b)) return false;
    if (a.isEditable() || b.isEditable()) return false;
    return labelsOf(a).equals(labelsOf(b));
  }

  private static List<String> labelsOf(JComboBox<?> box) {
    final var labels = new ArrayList<String>(box.getItemCount());
    for (var i = 0; i < box.getItemCount(); i++) {
      labels.add(Objects.toString(box.getItemAt(i), ""));
    }
    return labels;
  }
}
