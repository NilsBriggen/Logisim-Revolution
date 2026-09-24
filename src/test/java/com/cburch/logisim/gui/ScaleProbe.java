/*
 * Logisim-evolution - digital logic design tool and simulator
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui;

import com.cburch.logisim.prefs.AppPreferences;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;

/** Reports what the interface-scale guess actually sees on this machine. */
public final class ScaleProbe {
  private ScaleProbe() {}

  public static void main(String[] args) {
    System.out.println("headless          = " + GraphicsEnvironment.isHeadless());
    final var toolkitSize = Toolkit.getDefaultToolkit().getScreenSize();
    System.out.println("toolkit screen    = " + toolkitSize.width + "x" + toolkitSize.height);
    System.out.println("platform scale    = " + AppPreferences.getPlatformScaleFactor());
    System.out.println("auto scale        = " + AppPreferences.getAutoScaleFactor());
    System.out.println("legacy auto scale = " + AppPreferences.getLegacyAutoScaleFactorForProbe());
    System.out.println("stored Scale pref = "
        + AppPreferences.getPrefs().getDouble("Scale", Double.NaN));
    System.out.println("SCALE_FACTOR.get  = " + AppPreferences.SCALE_FACTOR.get());
    if (!GraphicsEnvironment.isHeadless()) {
      final var devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
      for (final var device : devices) {
        final var mode = device.getDisplayMode();
        final var transform = device.getDefaultConfiguration().getDefaultTransform();
        System.out.println(
            "device " + device.getIDstring()
                + "  mode=" + mode.getWidth() + "x" + mode.getHeight()
                + "  transform=" + transform.getScaleX() + "x" + transform.getScaleY());
      }
    }
    System.exit(0);
  }
}
