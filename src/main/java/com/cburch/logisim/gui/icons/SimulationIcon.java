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
import com.cburch.logisim.gui.theme.Tokens;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import javax.swing.Icon;

/**
 * The simulation controls: run, pause, step, and the manual clock ticks.
 *
 * <p>These sit at the end of the toolbar and are the buttons a student presses most, so they are
 * drawn from the shared icon set rather than as little clock faces built from filled ovals. Run
 * and stop carry a colour of their own, because whether the simulation is running is the one
 * thing worth spotting without reading.
 */
public class SimulationIcon implements Icon {

  public static final int SIM_PLAY = 0;
  public static final int SIM_PAUSE = 1;
  public static final int SIM_STEP = 2;
  public static final int SIM_ENABLE = 3;
  public static final int SIM_DISABLE = 4;
  public static final int SIM_HALF_TICK = 5;
  public static final int SIM_FULL_TICK = 6;

  private int currentType;

  public SimulationIcon(int type) {
    currentType = type;
  }

  /** Switches the glyph, for a button that reports state as well as offering an action. */
  public void setType(int type) {
    currentType = type;
  }

  private AppIcons.Id glyph() {
    return switch (currentType) {
      case SIM_PAUSE -> AppIcons.Id.PAUSE;
      case SIM_STEP -> AppIcons.Id.STEP;
      case SIM_ENABLE -> AppIcons.Id.TICK_ENABLED;
      case SIM_DISABLE -> AppIcons.Id.PAUSE;
      case SIM_HALF_TICK -> AppIcons.Id.TICK_HALF;
      case SIM_FULL_TICK -> AppIcons.Id.TICK_FULL;
      default -> AppIcons.Id.RUN;
    };
  }

  /** Green while the simulation can be started, amber while it is running and can be stopped. */
  private Color tint() {
    return switch (currentType) {
      case SIM_PLAY -> Tokens.success();
      case SIM_PAUSE, SIM_DISABLE -> Tokens.warning();
      default -> Tokens.iconForeground();
    };
  }

  private Icon icon() {
    return AppIcons.colored(glyph(), AppIcons.SIZE, tint());
  }

  @Override
  public void paintIcon(Component comp, Graphics gfx, int x, int y) {
    icon().paintIcon(comp, gfx, x, y);
  }

  @Override
  public int getIconWidth() {
    return icon().getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return icon().getIconHeight();
  }
}
