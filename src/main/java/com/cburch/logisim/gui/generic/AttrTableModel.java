/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

public interface AttrTableModel {
  void addAttrTableModelListener(AttrTableModelListener listener);

  AttrTableModelRow getRow(int rowIndex);

  int getRowCount();

  String getTitle();

  /**
   * Why the values cannot be changed, when what is shown is locked against edits; {@code null}
   * otherwise. Shown above the table so read-only rows do not look like a fault.
   */
  default String getEditLockNote() {
    return null;
  }

  void removeAttrTableModelListener(AttrTableModelListener listener);
}
