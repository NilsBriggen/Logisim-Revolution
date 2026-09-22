/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import com.cburch.logisim.prefs.AppPreferences;

/**
 * Single read path for the user interface scale factor.
 *
 * <p>The application scales its custom painted widgets by hand, multiplying design-time pixel
 * values by {@link AppPreferences#SCALE_FACTOR}. Historically callers reached for {@code
 * AppPreferences.getScaled()} directly, which spread the scaling policy across the whole code base
 * and made it impossible to change how scaling works in one place.
 *
 * <p>New user interface code should use this class instead. It intentionally offers nothing beyond
 * the existing behaviour today, but it gives later work (notably adopting the look and feel's own
 * high-DPI support) a single place to change.
 */
public final class UiScale {

  private UiScale() {
    // Utility class, not instantiable.
  }

  /** Returns the raw scale factor, where {@code 1.0} means no scaling. */
  public static double factor() {
    return AppPreferences.SCALE_FACTOR.get();
  }

  /** Scales a design-time pixel value, rounding to the nearest device pixel. */
  public static int scaled(int value) {
    return AppPreferences.getScaled(value);
  }

  /** Scales a design-time pixel value without rounding. */
  public static float scaled(float value) {
    return AppPreferences.getScaled(value);
  }

  /** Scales a design-time pixel value without rounding. */
  public static double scaled(double value) {
    return AppPreferences.getScaled(value);
  }

  /** Converts a device pixel value back to its design-time equivalent. */
  public static int unscaled(int value) {
    return AppPreferences.getDownScaled(value);
  }

  /** Returns the edge length of a scaled toolbar or tree icon, in device pixels. */
  public static int iconSize() {
    return AppPreferences.getIconSize();
  }

  /** Returns the scaled padding drawn around an icon, in device pixels. */
  public static int iconBorder() {
    return AppPreferences.getIconBorder();
  }
}
