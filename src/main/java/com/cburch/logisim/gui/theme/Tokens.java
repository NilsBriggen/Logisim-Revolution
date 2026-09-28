/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.theme;

import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.Color;
import javax.swing.UIManager;

/**
 * The colours the application paints with, by role rather than by value.
 *
 * <p>Every one comes from the active theme's property file under a {@code Logisim.} key, so a
 * colour is changed in one place and nothing has to be hunted down in painting code. The fallbacks
 * exist only so that a component still renders if it is built before a theme is installed, which
 * happens in tests.
 *
 * <p>Fonts and spacing live in {@link UiFonts} and {@link Spacing}; they are re-exposed here so
 * that new code has a single place to reach for.
 */
public final class Tokens {

  private Tokens() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /** The colour that marks the active thing: selection, current circuit, focus. */
  public static Color accent() {
    return color("Logisim.accent", fallback(0x4C8DFF, 0x2463D1));
  }

  public static Color accentText() {
    return color("Logisim.accentText", Color.WHITE);
  }

  /** Something needs attention but still works. */
  public static Color warning() {
    return color("Logisim.warning", fallback(0xF0B429, 0x8A5A0C));
  }

  /** Something failed. */
  public static Color error() {
    return color("Logisim.error", fallback(0xF87171, 0xB42D2D));
  }

  /** Something succeeded. */
  public static Color success() {
    return color("Logisim.success", fallback(0x4ADE80, 0x17703F));
  }

  public static Color activityBarBackground() {
    return color("Logisim.activityBar.background", fallback(0x252526, 0xF0F0F0));
  }

  public static Color activityBarForeground() {
    return color("Logisim.activityBar.foreground", fallback(0x9A9A9A, 0x5A5A5A));
  }

  public static Color sidePanelHeaderForeground() {
    return color("Logisim.sidePanel.headerForeground", fallback(0x9A9A9A, 0x5A5A5A));
  }

  public static Color statusBarBackground() {
    return color("Logisim.statusBar.background", fallback(0x2D2D30, 0xE8E8E8));
  }

  public static Color statusBarForeground() {
    return color("Logisim.statusBar.foreground", fallback(0xCCCCCC, 0x333333));
  }

  /** Separator lines between the shell's regions. */
  public static Color divider() {
    return color("Logisim.divider", color("Separator.foreground", fallback(0x3C3C3C, 0xD0D0D0)));
  }

  /** Text that is present but secondary: hints, counts, units. */
  public static Color mutedForeground() {
    return color("Logisim.mutedForeground", fallback(0x9A9A9A, 0x6B6B6B));
  }

  public static Color badgeBackground() {
    return color("Logisim.badge.background", fallback(0x3C3C3C, 0xDCDCDC));
  }

  public static Color toastBackground() {
    return color("Logisim.toast.background", fallback(0x2D2D30, 0xF5F5F5));
  }

  /** The tint of an icon drawn on the current surface. */
  public static Color iconForeground() {
    return color("Logisim.icon.foreground", fallback(0xCCCCCC, 0x3C3C3C));
  }

  public static Color iconDisabled() {
    return color("Logisim.icon.disabled", fallback(0x6E6E6E, 0xA6A6A6));
  }

  /** The recessed well behind a print or export preview, which the white page stands out on. */
  public static Color previewBackground() {
    return color("Logisim.preview.background", fallback(0x181A1E, 0xE2E4E8));
  }

  /** The ring drawn around a component the pointer is about to act on. */
  public static Color canvasHalo() {
    return color("Logisim.canvas.halo", accent());
  }

  /** Design-time gap, in logical pixels, between an icon and its text in a list or tree row. */
  public static final int ICON_TEXT_GAP = 6;

  /**
   * The gap between an icon and its label in list and tree rows, scaled.
   *
   * <p>Every custom cell renderer uses this, so the toolbar editor's list no longer draws its tool
   * icons touching their names while the explorer tree beside it leaves a gap.
   */
  public static int iconTextGap() {
    return UiScale.scaled(ICON_TEXT_GAP);
  }

  /** Vertical padding, scaled, above and below the content of a list or tree row. */
  public static int rowPadding() {
    return Spacing.xs() / 2;
  }

  /** Reads {@code key}, or returns {@code fallback} when no theme is installed. */
  public static Color color(String key, Color fallback) {
    final var color = UIManager.getColor(key);
    return color != null ? new Color(color.getRGB(), true) : fallback;
  }

  /** Picks between a dark-theme and a light-theme literal. */
  private static Color fallback(int darkRgb, int lightRgb) {
    return new Color(Theme.isDark() ? darkRgb : lightRgb);
  }
}
