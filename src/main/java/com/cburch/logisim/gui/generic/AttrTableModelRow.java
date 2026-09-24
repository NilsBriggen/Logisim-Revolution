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
import java.util.List;

public interface AttrTableModelRow {
  @FunctionalInterface
  interface Edit {
    void commit(Object value) throws AttrTableSetException;
  }

  /** Captures the destination before selection changes or editor teardown. */
  default Edit captureEdit(Window parent, List<AttrTableModelRow> rows) {
    return value -> setValue(parent, value);
  }

  /** Whether this model can commit selected rows as one undoable operation. */
  default boolean supportsMultiEdit() {
    return false;
  }

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

  /** Formats an editor value for a pending, uncommitted wheel preview. */
  default String displayValue(Object value) {
    return String.valueOf(value);
  }

  boolean isValueEditable();

  boolean multiEditCompatible(AttrTableModelRow other);

  void setValue(Window parent, Object value) throws AttrTableSetException;
}
