/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.prefs.AppPreferences;
import com.formdev.flatlaf.util.UIScale;
import java.beans.PropertyChangeListener;
import javax.swing.SwingUtilities;

/**
 * Single read path for the user interface scale factor.
 *
 * <p>FlatLaf owns the effective scale of fonts, native control metrics and custom widgets. Values
 * here are Swing coordinates, before Java2D's device transform. Already scaled look-and-feel fonts
 * and SVG icons must not be scaled again. Circuit coordinates and document zoom are independent.
 */
public final class UiScale {

  private static boolean installed;
  // Preference listeners are weak: retain the application-lifetime subscription here.
  private static final PropertyChangeListener SCALE_LISTENER = event -> refresh();

  private UiScale() {
    // Utility class, not instantiable.
  }

  /** Returns the raw scale factor, where {@code 1.0} means no scaling. */
  public static double factor() {
    return UIScale.getUserScaleFactor();
  }

  /** Installs the preference bridge after the look and feel has been installed, on the EDT. */
  public static void install() {
    if (!installed) {
      AppPreferences.SCALE_FACTOR.addPropertyChangeListener(SCALE_LISTENER);
      installed = true;
      DesktopScale.detect().thenRun(() -> SwingUtilities.invokeLater(UiScale::refresh));
    }
    applyPreferences();
  }

  /** Applies an explicit effective scale, without writing preferences or touching document zoom. */
  public static boolean setFactor(double factor) {
    if (!Double.isFinite(factor) || factor <= 0) {
      throw new IllegalArgumentException("Scale must be finite and positive");
    }
    final var base = UIScale.getUserScaleFactor() / UIScale.getZoomFactor();
    return UIScale.setZoomFactor((float) (factor / base));
  }

  /** Reads auto mode from the absence of a stored Scale, preserving every explicit value. */
  private static boolean applyPreferences() {
    final var explicit = AppPreferences.getPrefs().get("Scale", null);
    final var requested = explicit == null
        ? AppPreferences.getAutoScaleFactor()
        : DesktopScale.effectiveFactor(AppPreferences.SCALE_FACTOR.get(),
            UIScale.getUserScaleFactor() / UIScale.getZoomFactor(),
            AppPreferences.getPlatformScaleFactor(), Double.NaN);
    return setFactor(Double.isFinite(requested) && requested > 0 ? requested : 1.0);
  }

  /** Applies scale on the EDT, deferring delegate refresh until the active handler returns. */
  public static void refresh() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(UiScale::refresh);
      return;
    }
    if (applyPreferences()) {
      Theme.refreshUiLater();
    }
  }

  /** Scales a design-time value, rounding to the nearest Swing coordinate. */
  public static int scaled(int value) {
    return UIScale.scale(value);
  }

  /** Scales a design-time pixel value without rounding. */
  public static float scaled(float value) {
    return UIScale.scale(value);
  }

  /** Scales a design-time pixel value without rounding. */
  public static double scaled(double value) {
    return value * factor();
  }

  /** Converts a Swing coordinate back to its design-time equivalent. */
  public static int unscaled(int value) {
    return UIScale.unscale(value);
  }

  /** Returns the edge length of a scaled toolbar or tree icon, in Swing coordinates. */
  public static int iconSize() {
    return AppPreferences.getIconSize();
  }

  /** Returns the scaled padding drawn around an icon, in Swing coordinates. */
  public static int iconBorder() {
    return AppPreferences.getIconBorder();
  }
}
