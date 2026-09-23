/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.icons;

import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Tokens;
import java.awt.Component;
import java.awt.Graphics;
import javax.swing.Icon;

/**
 * The folder shown beside a library in the project tree.
 *
 * <p>It used to be a drawing of a manila folder with a sheet of paper and a shadow, in yellow and
 * grey whatever the theme. A dozen of them down the side of the window were the loudest thing on
 * screen while saying the least; it is now a quiet outline in the interface colour.
 */
public class TreeIcon implements Icon {

  private final boolean closed;

  public TreeIcon(boolean closed) {
    this.closed = closed;
  }

  private Icon icon() {
    return AppIcons.colored(AppIcons.Id.FOLDER, AppIcons.SIZE, Tokens.mutedForeground());
  }

  @Override
  public void paintIcon(Component comp, Graphics gfx, int x, int y) {
    icon().paintIcon(comp, gfx, x, y);
  }

  @Override
  public int getIconWidth() {
    return icon().getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return icon().getIconHeight();
  }

  /** Whether this is the closed-folder variant. Kept so both states can be told apart. */
  public boolean isClosed() {
    return closed;
  }
}
