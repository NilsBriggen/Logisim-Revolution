/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */


package com.cburch.logisim.gui.icons;

import java.awt.Graphics2D;

/** Semantic module thumbnail, shared with the component picker. */
public class CircuitIcon extends BaseIcon {
  @Override
  protected void paintIcon(Graphics2D graphics) {
    ComponentIcons.module().paintIcon(null, graphics, 0, 0);
  }
}
