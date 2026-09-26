/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.menu;

import java.awt.event.ActionEvent;

public abstract class EditHandler {
  private Listener listener;

  public void cut() {}

  public void copy() {}

  public void paste() {}

  public void delete() {}

  public void duplicate() {}

  public void selectAll() {}

  public void raise() {}

  public void lower() {}

  public void raiseTop() {}

  public void lowerBottom() {}

  public void addControlPoint() {}

  public void removeControlPoint() {}

  /**
   * Runs one of the Edit &gt; Arrange commands ({@link LogisimMenuBar#ARRANGE_ITEMS}). Handlers
   * that cannot arrange their selection leave it alone.
   */
  public void arrange(LogisimMenuItem item) {}

  public abstract void computeEnabled();

  protected void setEnabled(LogisimMenuItem action, boolean value) {
    final var l = listener;
    if (l != null) {
      l.enableChanged(this, action, value);
    }
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  public void actionPerformed(ActionEvent e) {
    final var src = e.getSource();
    if (src == LogisimMenuBar.CUT) cut();
    else if (src == LogisimMenuBar.COPY) copy();
    else if (src == LogisimMenuBar.PASTE) paste();
    else if (src == LogisimMenuBar.DELETE) delete();
    else if (src == LogisimMenuBar.DUPLICATE) duplicate();
    else if (src == LogisimMenuBar.SELECT_ALL) selectAll();
    else if (src == LogisimMenuBar.RAISE) raise();
    else if (src == LogisimMenuBar.LOWER) lower();
    else if (src == LogisimMenuBar.RAISE_TOP) raiseTop();
    else if (src == LogisimMenuBar.LOWER_BOTTOM) lowerBottom();
    else if (src == LogisimMenuBar.ADD_CONTROL) addControlPoint();
    else if (src == LogisimMenuBar.REMOVE_CONTROL) removeControlPoint();
    else if (src instanceof LogisimMenuItem item && isArrangeItem(item)) arrange(item);
  }

  private static boolean isArrangeItem(LogisimMenuItem item) {
    for (final var arrangeItem : LogisimMenuBar.ARRANGE_ITEMS) {
      if (arrangeItem == item) return true;
    }
    return false;
  }

  public interface Listener {
    void enableChanged(EditHandler handler, LogisimMenuItem action, boolean value);
  }
}
