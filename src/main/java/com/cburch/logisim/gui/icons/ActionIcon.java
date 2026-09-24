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

/** Compatibility base for action icons backed by the shared, live-scaled SVG registry. */
public abstract class ActionIcon extends BaseIcon {
  protected abstract AppIcons.Id iconId();

  @Override
  protected void paintIcon(Graphics2D graphics) {
    AppIcons.get(iconId(), AppPreferences.IconSize).paintIcon(null, graphics, 0, 0);
  }
}
