/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */


package com.cburch.logisim.tools;

import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.util.StringUtil;
import java.awt.Color;

/** An unregistered extension-style factory whose own painter must remain authoritative. */
public class IconTestFactory extends InstanceFactory {
  public static final String _ID = "Icon test fixture";

  public IconTestFactory() {
    super(_ID, StringUtil.constantGetter(_ID));
  }

  @Override
  public void paintIcon(InstancePainter painter) {
    final var graphics = painter.getGraphics();
    graphics.setColor(Color.MAGENTA);
    graphics.fillRect(4, 5, 7, 6);
  }

  @Override
  public void propagate(InstanceState state) {}

  @Override
  public void paintInstance(InstancePainter painter) {}
}
