/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.draw.icons;

import com.cburch.logisim.gui.icons.ActionIcon;
import com.cburch.logisim.gui.theme.AppIcons;

public class DrawPolylineIcon extends ActionIcon {
  private final boolean closed;

  public DrawPolylineIcon(boolean closed) {
    this.closed = closed;
  }

  @Override
  protected AppIcons.Id iconId() {
    return closed ? AppIcons.Id.DRAW_POLYGON : AppIcons.Id.DRAW_POLYLINE;
  }
}
