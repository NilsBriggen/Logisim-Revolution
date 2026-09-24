/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.soc.gui;

import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.Color;

/**
 * How a processor's state is drawn.
 *
 * <p>A CPU shows its registers, its interrupt lines and its trace on the circuit canvas, and the
 * attribute that turns that on is set by default. It was painted as a bright yellow slab with a
 * royal-blue header, white cells holding blue figures and a magenta program counter, dropped
 * straight onto a canvas that had been restyled around it.
 *
 * <p>The roles are named here so the panel takes the theme, and so printing still gets ink on
 * paper rather than a screen palette.
 */
public final class CpuStyle {

  private CpuStyle() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  private static boolean printing() {
    return AppPreferences.inPrintView();
  }

  /** The panel the state is drawn on. */
  public static Color surface() {
    return printing() ? Color.WHITE : Tokens.badgeBackground();
  }

  /** The strip across the top of a panel carrying its name. */
  public static Color header() {
    return printing() ? CanvasStyle.componentColor() : Tokens.accent();
  }

  /** Text on that strip, and on anything else filled with the header colour. */
  public static Color headerText() {
    return printing() ? Color.WHITE : Tokens.accentText();
  }

  /** Outlines and ordinary text. */
  public static Color ink() {
    return CanvasStyle.componentColor();
  }

  /** The inside of a register or flag cell. */
  public static Color cell() {
    return printing() ? Color.WHITE : Tokens.toastBackground();
  }

  /** A register that was written by the instruction just executed. */
  public static Color highlight() {
    return header();
  }

  /** A register's ABI name, which is a note beside its number rather than the value. */
  public static Color muted() {
    return Tokens.mutedForeground();
  }

  /** The program counter, which is the one thing worth picking out of the trace. */
  public static Color programCounter() {
    return printing() ? CanvasStyle.componentColor() : Tokens.warning();
  }
}
