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

public class OpenSaveIcon extends ActionIcon {
  public static final int FILE_OPEN = 0;
  public static final int FILE_SAVE = 1;
  public static final int FILE_SAVE_AS = 2;
  private final int type;

  public OpenSaveIcon(int type) {
    this.type = type;
  }

  @Override
  protected AppIcons.Id iconId() {
    return switch (type) {
      case FILE_OPEN -> AppIcons.Id.OPEN;
      case FILE_SAVE_AS -> AppIcons.Id.SAVE_AS;
      default -> AppIcons.Id.SAVE;
    };
  }
}
