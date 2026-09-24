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

public class ZoomIcon extends ActionIcon {
  public static final int ZOOMIN = 0;
  public static final int ZOOMOUT = 1;
  public static final int NOZOOM = 2;

  private final int zoomType;

  public ZoomIcon() {
    this(NOZOOM);
  }

  public ZoomIcon(int type) {
    zoomType = type;
  }

  @Override
  protected AppIcons.Id iconId() {
    return switch (zoomType) {
      case ZOOMIN -> AppIcons.Id.ZOOM_IN;
      case ZOOMOUT -> AppIcons.Id.ZOOM_OUT;
      default -> AppIcons.Id.SEARCH;
    };
  }
}
