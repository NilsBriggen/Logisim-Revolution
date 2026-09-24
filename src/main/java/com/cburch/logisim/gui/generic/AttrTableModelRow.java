/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import com.cburch.logisim.data.Attribute;
import java.awt.Component;
import java.awt.Window;

public interface AttrTableModelRow {
  Component getEditor(Window parent);

  /**
   * The attribute behind this row, when there is one.
   *
   * <p>Lets the panel offer more than a name and a value: what the attribute does, which group it
   * belongs in, and which editor suits its type. Rows that stand for something else, such as the
   * HDL support notice, have no attribute and return {@code null}.
   */
  default Attribute<?> getAttribute() {
    return null;
  }

  String getLabel();

  String getValue();

  boolean isValueEditable();

  boolean multiEditCompatible(AttrTableModelRow other);

  void setValue(Window parent, Object value) throws AttrTableSetException;
}
