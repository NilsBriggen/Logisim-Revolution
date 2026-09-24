/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.icons;

import com.cburch.logisim.data.Direction;
import com.cburch.logisim.gui.theme.AppIcons;

public class FatArrowIcon extends ActionIcon {
  private final Direction direction;

  public FatArrowIcon(Direction direction) {
    this.direction = direction;
  }

  @Override
  protected AppIcons.Id iconId() {
    if (direction == Direction.WEST) return AppIcons.Id.ARROW_LEFT;
    if (direction == Direction.SOUTH) return AppIcons.Id.ARROW_DOWN;
    if (direction == Direction.EAST) return AppIcons.Id.ARROW_RIGHT;
    return AppIcons.Id.ARROW_UP;
  }
}
