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

public class ProjectAddIcon extends BaseIcon {
  private final AppIcons.Id id;
  private boolean deselect;

  public ProjectAddIcon() {
    id = AppIcons.Id.HDL;
  }

  public ProjectAddIcon(boolean removeSymbol) {
    id = removeSymbol ? AppIcons.Id.DELETE : AppIcons.Id.ADD;
  }

  public void setDeselect(boolean value) {
    deselect = value;
  }

  @Override
  protected void paintIcon(Graphics2D graphics) {
    final var icon = deselect ? AppIcons.disabled(id, AppPreferences.IconSize)
        : AppIcons.get(id, AppPreferences.IconSize);
    icon.paintIcon(null, graphics, 0, 0);
  }
}
