/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import com.cburch.logisim.gui.theme.SystemTheme;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Desktop-advertised logical DPI, separate from screen resolution and Java2D device scaling. */
public final class DesktopScale {
  private static volatile double desktopDpi = Double.NaN;
  private static CompletableFuture<Double> detection;

  private DesktopScale() {}

  /**
   * Queries once on a worker. Toolkit font scaling remains FlatLaf's responsibility; Xft DPI is
   * consulted only to fill the gap left by an unscaled Xwayland Java toolkit. Missing properties,
   * commands and displays leave Auto on FlatLaf's own scale.
   */
  public static synchronized CompletableFuture<Double> detect() {
    if (detection != null) return detection;
    detection = new CompletableFuture<>();
    if (GraphicsEnvironment.isHeadless()) {
      detection.complete(Double.NaN);
      return detection;
    }
    Thread.ofVirtual().name("desktop-dpi-probe").start(() -> {
      var dpi = Double.NaN;
      try {
        final var toolkit = Toolkit.getDefaultToolkit();
        dpi = parseDpi(toolkit.getDesktopProperty("gnome.Xft/DPI"), true);
        if (!Double.isFinite(dpi)) {
          dpi = parseDpi(toolkit.getDesktopProperty("Xft.dpi"), false);
        }
        // xrdb reads the selected X server's resources, never the user's configuration files.
        if (!Double.isFinite(dpi)
            && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")
            && System.getenv("DISPLAY") != null && !System.getenv("DISPLAY").isBlank()) {
          dpi = parseXResources(SystemTheme.queryXResources());
        }
      } catch (RuntimeException ignored) {
        // An unavailable desktop service is not a reason to fail startup.
      }
      desktopDpi = dpi;
      detection.complete(dpi);
    });
    return detection;
  }

  /** Returns the best known Auto factor; this read never launches a process or blocks Swing. */
  public static double autoFactor(double nativeFactor, double deviceFactor) {
    return effectiveFactor(null, nativeFactor, deviceFactor, desktopDpi);
  }

  /** Pure policy: respect explicit choices and do not multiply scale already supplied by a layer. */
  static double effectiveFactor(
      Double explicitFactor, double nativeFactor, double deviceFactor, double advertisedDpi) {
    if (explicitFactor != null && Double.isFinite(explicitFactor) && explicitFactor > 0) {
      return explicitFactor;
    }
    final var nativeScale = finitePositive(nativeFactor) ? nativeFactor : 1.0;
    final var deviceScale = finitePositive(deviceFactor) ? deviceFactor : 1.0;
    if (!validDpi(advertisedDpi)) return nativeScale;
    return Math.max(nativeScale, advertisedDpi / 96.0 / deviceScale);
  }

  static double parseDpi(Object value, boolean fixedPoint) {
    if (value == null) return Double.NaN;
    try {
      final var raw = value instanceof Number number
          ? number.doubleValue() : Double.parseDouble(value.toString().trim());
      final var dpi = fixedPoint ? raw / 1024.0 : raw;
      return validDpi(dpi) ? dpi : Double.NaN;
    } catch (NumberFormatException ignored) {
      return Double.NaN;
    }
  }

  static double parseXResources(String resources) {
    if (resources == null) return Double.NaN;
    for (final var line : resources.split("\\R")) {
      final var separator = line.indexOf(':');
      if (separator < 0) continue;
      if (line.substring(0, separator).trim().equalsIgnoreCase("Xft.dpi")) {
        return parseDpi(line.substring(separator + 1), false);
      }
    }
    return Double.NaN;
  }

  private static boolean validDpi(double dpi) {
    // Reject unset sentinels and implausible data rather than creating unusable window sizes.
    return Double.isFinite(dpi) && dpi >= 48.0 && dpi <= 768.0;
  }

  private static boolean finitePositive(double value) {
    return Double.isFinite(value) && value > 0;
  }
}
