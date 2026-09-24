/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.UiScale;
import java.util.prefs.Preferences;

/**
 * Where the window's panels sit, remembered between runs.
 *
 * <p>Sizes are kept in logical pixels rather than as a fraction of the window. A side panel is a
 * column of labels: it needs the width its contents need, not a share of however wide the window
 * happens to be, and a fraction made the explorer swell on a wide screen and become unusable on a
 * narrow one.
 *
 * <p>The old fractions are converted once, so an existing window comes back roughly as it was.
 */
public final class LayoutPrefs {

  /** Widths and heights are clamped to this range, so a panel can never be lost off an edge. */
  public static final int MIN_PANEL = 160;

  public static final int MAX_PANEL = 720;

  /**
   * How wide a converted side panel may end up.
   *
   * <p>The old layout stored a share of the window, so a quarter of a wide screen produced a
   * column several hundred pixels wider than the list of names it holds. Converting that share
   * faithfully would carry the problem over, so the result is capped at a width an explorer can
   * actually use; dragging it wider afterwards still works.
   */
  private static final int MAX_MIGRATED_SIDE = 360;

  public static final int DEFAULT_SIDE_WIDTH = 260;
  public static final int DEFAULT_INSPECTOR_WIDTH = 280;
  public static final int DEFAULT_BOTTOM_HEIGHT = 200;

  private static final String SIDE_WIDTH = "shell.sideWidth";
  private static final String INSPECTOR_WIDTH = "shell.inspectorWidth";
  private static final String BOTTOM_HEIGHT = "shell.bottomHeight";
  private static final String SIDE_VISIBLE = "shell.sideVisible";
  private static final String INSPECTOR_VISIBLE = "shell.inspectorVisible";
  private static final String BOTTOM_VISIBLE = "shell.bottomVisible";
  private static final String ACTIVE_VIEW = "shell.activeSideView";
  private static final String MIGRATED = "shell.migrated";
  private static final String LOGICAL_SIZES = "shell.logicalSizes";

  private LayoutPrefs() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /**
   * Converts the fractions the previous layout stored into pixel widths, once.
   *
   * @param windowWidth the stored window width the fractions were relative to
   * @param windowHeight the stored window height
   */
  public static void migrate(int windowWidth, int windowHeight) {
    migrate(AppPreferences.getPrefs(), windowWidth, windowHeight, UiScale.factor());
  }

  /** Only explicit legacy values are migrated; missing values mean a fresh logical layout. */
  static void migrate(Preferences prefs, int windowWidth, int windowHeight, double scale) {
    migrateSizes(prefs, scale);
    if (prefs.getBoolean(MIGRATED, false)) return;

    if (windowWidth > 0 && prefs.get(SIDE_WIDTH, null) == null) {
      final var sideFraction = prefs.getDouble("windowMainSplit", Double.NaN);
      if (sideFraction > 0 && sideFraction < 1) {
        final var width = (int) Math.round(windowWidth * sideFraction / scale);
        prefs.putInt(SIDE_WIDTH, Math.min(MAX_MIGRATED_SIDE, clamp(width)));
      }
    }
    if (windowHeight > 0 && prefs.get(BOTTOM_HEIGHT, null) == null) {
      // The VHDL console used to take the space below the canvas; that is now the bottom panel.
      final var consoleFraction = prefs.getDouble("windowRightSplit", Double.NaN);
      if (consoleFraction > 0 && consoleFraction < 1) {
        prefs.putInt(
            BOTTOM_HEIGHT,
            clamp((int) Math.round(windowHeight * (1.0 - consoleFraction) / scale)));
      }
    }
    if (prefs.get(SIDE_VISIBLE, null) == null && prefs.get("windowExplorerVisible", null) != null) {
      prefs.putBoolean(SIDE_VISIBLE, prefs.getBoolean("windowExplorerVisible", true));
    }
    prefs.putBoolean(MIGRATED, true);
  }

  /** Keeps a size inside the range a panel can usefully take. */
  public static int clamp(int size) {
    return Math.max(MIN_PANEL, Math.min(MAX_PANEL, size));
  }

  public static int sideWidth() {
    return readSize(SIDE_WIDTH, DEFAULT_SIDE_WIDTH);
  }

  public static void setSideWidth(int width) {
    writeSize(SIDE_WIDTH, width);
  }

  public static int inspectorWidth() {
    return readSize(INSPECTOR_WIDTH, DEFAULT_INSPECTOR_WIDTH);
  }

  public static void setInspectorWidth(int width) {
    writeSize(INSPECTOR_WIDTH, width);
  }

  public static int bottomHeight() {
    return readSize(BOTTOM_HEIGHT, DEFAULT_BOTTOM_HEIGHT);
  }

  public static void setBottomHeight(int height) {
    writeSize(BOTTOM_HEIGHT, height);
  }

  /** Converts the old device-pixel dimensions once, without scaling fonts or circuit geometry. */
  static void migrateSizes(Preferences prefs, double scale) {
    if (prefs.getBoolean(LOGICAL_SIZES, false)) return;
    for (final var key : new String[] {SIDE_WIDTH, INSPECTOR_WIDTH, BOTTOM_HEIGHT}) {
      if (prefs.get(key, null) != null) {
        prefs.putInt(key, clamp((int) Math.round(prefs.getInt(key, MIN_PANEL) / scale)));
      }
    }
    prefs.putBoolean(LOGICAL_SIZES, true);
  }

  private static int readSize(String key, int fallback) {
    final var prefs = AppPreferences.getPrefs();
    migrateSizes(prefs, UiScale.factor());
    return UiScale.scaled(clamp(prefs.getInt(key, fallback)));
  }

  private static void writeSize(String key, int deviceSize) {
    final var prefs = AppPreferences.getPrefs();
    migrateSizes(prefs, UiScale.factor());
    prefs.putInt(key, clamp(UiScale.unscaled(deviceSize)));
  }

  public static boolean sideVisible() {
    return AppPreferences.getPrefs().getBoolean(SIDE_VISIBLE, true);
  }

  public static void setSideVisible(boolean visible) {
    AppPreferences.getPrefs().putBoolean(SIDE_VISIBLE, visible);
  }

  public static boolean inspectorVisible() {
    return AppPreferences.getPrefs().getBoolean(INSPECTOR_VISIBLE, true);
  }

  public static void setInspectorVisible(boolean visible) {
    AppPreferences.getPrefs().putBoolean(INSPECTOR_VISIBLE, visible);
  }

  public static boolean bottomVisible() {
    return AppPreferences.getPrefs().getBoolean(BOTTOM_VISIBLE, false);
  }

  public static void setBottomVisible(boolean visible) {
    AppPreferences.getPrefs().putBoolean(BOTTOM_VISIBLE, visible);
  }

  public static String activeSideView() {
    return AppPreferences.getPrefs().get(ACTIVE_VIEW, "");
  }

  public static void setActiveSideView(String id) {
    AppPreferences.getPrefs().put(ACTIVE_VIEW, id == null ? "" : id);
  }

  /** Restores the shipped layout, as the preferences window's reset button does. */
  public static void reset() {
    setSideWidth(UiScale.scaled(DEFAULT_SIDE_WIDTH));
    setInspectorWidth(UiScale.scaled(DEFAULT_INSPECTOR_WIDTH));
    setBottomHeight(UiScale.scaled(DEFAULT_BOTTOM_HEIGHT));
    setSideVisible(true);
    setInspectorVisible(true);
    setBottomVisible(false);
  }
}
