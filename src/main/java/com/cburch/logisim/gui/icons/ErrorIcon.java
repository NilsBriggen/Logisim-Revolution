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
 * Marks something that failed.
 *
 * <p>Drawn from the shared icon set rather than by hand, so it matches the rest of the interface
 * and stays sharp at any scale. The colour comes from the theme.
 */
public class ErrorIcon implements Icon {

  private final int size;
  private AppIcons.Id glyph = AppIcons.Id.ERROR;

  public ErrorIcon() {
    this(1.0);
  }

  /**
   * @param scale multiple of the standard icon size
   */
  public ErrorIcon(double scale) {
    size = (int) Math.round(AppIcons.SIZE * scale);
  }

  /**
   * @param size edge length in unscaled pixels
   */
  public ErrorIcon(int size) {
    this.size = size;
  }

  /**
   * The icon for a button that steps through the errors found in a listing.
   *
   * <p>A direction reads better than an error mark here: the button does not report an error, it
   * moves to one.
   *
   * @param forward towards the next error
   * @param backward towards the previous error
   */
  public ErrorIcon(boolean forward, boolean backward) {
    this(1.0);
    if (forward) {
      glyph = AppIcons.Id.CHEVRON_RIGHT;
    } else if (backward) {
      glyph = AppIcons.Id.CHEVRON_LEFT;
    }
  }

  /** Resolved on each paint, so the icon follows a theme change without being rebuilt. */
  private Icon icon() {
    return AppIcons.colored(glyph, size, Tokens.error());
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
