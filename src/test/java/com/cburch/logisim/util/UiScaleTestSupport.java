/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Helper for tests that need to change the interface scale factor.
 *
 * <p>Only in-memory look-and-feel state changes. These tests never write a Scale preference.
 */
final class UiScaleTestSupport {

  private UiScaleTestSupport() {
    // Utility class, not instantiable.
  }

  /** Isolates native fonts from developer-default overrides left by other Swing fixtures. */
  static final class NativeFonts implements AutoCloseable {
    private Object labelOverride;
    private LookAndFeel lookAndFeel;
    private float zoom;

    NativeFonts() throws Exception {
      SwingUtilities.invokeAndWait(() -> {
        lookAndFeel = UIManager.getLookAndFeel();
        zoom = UIScale.getZoomFactor();
        // get("Label.font") would resolve a LaF font and freeze it as a developer override.
        labelOverride = UIManager.put("Label.font", null);
        FlatLightLaf.setup();
      });
    }

    @Override
    public void close() throws Exception {
      SwingUtilities.invokeAndWait(() -> {
        try {
          UIManager.setLookAndFeel(lookAndFeel);
        } catch (javax.swing.UnsupportedLookAndFeelException e) {
          throw new AssertionError(e);
        } finally {
          UIScale.setZoomFactor(zoom);
          UIManager.put("Label.font", labelOverride);
        }
      });
    }
  }

  /** Applies metrics on the EDT without touching stored application preferences. */
  static synchronized void setScaleFactor(double scale) {
    try {
      SwingUtilities.invokeAndWait(() -> {
        if (!(javax.swing.UIManager.getLookAndFeel() instanceof FlatLaf)) FlatLightLaf.setup();
        UiScale.setFactor(scale);
      });
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
