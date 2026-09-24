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

public class DrawShapeIcon extends ActionIcon {
  public static final int RECTANGLE = 0;
  public static final int ROUNDED_RECTANGLE = 1;
  public static final int ELIPSE = 2;
  private final int shapeType;

  public DrawShapeIcon(int type) {
    shapeType = type;
  }

  @Override
  protected AppIcons.Id iconId() {
    return switch (shapeType) {
      case RECTANGLE -> AppIcons.Id.DRAW_RECTANGLE;
      case ROUNDED_RECTANGLE -> AppIcons.Id.DRAW_ROUNDED_RECTANGLE;
      default -> AppIcons.Id.DRAW_ELLIPSE;
    };
  }
}
