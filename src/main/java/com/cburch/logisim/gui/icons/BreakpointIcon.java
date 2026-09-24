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
 * Marks a breakpoint in the assembler and SoC debugger.
 *
 * <p>Drawn from the shared icon set rather than by hand, so it matches the rest of the interface
 * and stays sharp at any scale. The colour comes from the theme.
 */
public class BreakpointIcon implements Icon {

  private final int size;

  public BreakpointIcon() {
    this(1.0);
  }

  /**
   * @param scale multiple of the standard icon size
   */
  public BreakpointIcon(double scale) {
    size = (int) Math.round(AppIcons.SIZE * scale);
  }

  /** Resolved on each paint, so the icon follows a theme change without being rebuilt. */
  private Icon icon() {
    return AppIcons.colored(AppIcons.Id.BREAKPOINT, size, Tokens.error());
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
}
