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
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.Graphics2D;

public class ShowStateIcon extends BaseIcon {
  private final boolean pressed;

  public ShowStateIcon(boolean pressed) {
    this.pressed = pressed;
  }

  @Override
  protected void paintIcon(Graphics2D graphics) {
    final var icon = pressed
        ? AppIcons.accented(AppIcons.Id.EYE, AppPreferences.IconSize)
        : AppIcons.get(AppIcons.Id.EYE, AppPreferences.IconSize);
    icon.paintIcon(null, graphics, 0, 0);
  }
}
