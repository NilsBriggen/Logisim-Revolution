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
import com.cburch.logisim.vhdl.gui.HdlToolbarModel;

/** Compatibility wrapper for the three HDL editor actions. */
public class HdlIcon extends ActionIcon {
  private final String type;

  public HdlIcon(String type) {
    this.type = type;
  }

  @Override
  protected AppIcons.Id iconId() {
    return switch (type) {
      case HdlToolbarModel.HDL_VALIDATE -> AppIcons.Id.CHECK;
      case HdlToolbarModel.HDL_EXPORT -> AppIcons.Id.EXPORT;
      default -> AppIcons.Id.OPEN;
    };
  }
}
