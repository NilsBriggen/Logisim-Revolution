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

public class DrcIcon extends BaseIcon {
  public final boolean drawEmpty;

  public DrcIcon(boolean isDrcError) {
    drawEmpty = !isDrcError;
  }

  @Override
  protected void paintIcon(Graphics2D graphics) {
    // An absent DRC error is not proof that checks have run successfully.
    if (!drawEmpty) {
      AppIcons.get(AppIcons.Id.ERROR, AppPreferences.IconSize).paintIcon(null, graphics, 0, 0);
    }
  }
}
