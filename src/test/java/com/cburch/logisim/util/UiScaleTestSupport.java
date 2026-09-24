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
 * Helper for tests that need to change the interface scale factor.
 *
 * <p>The preference store these tests write to is the developer's own. A run of the suite used to
 * leave the interface scale wherever the last test had put it — in practice 1.0 — so the
 * application came up uncomfortably small afterwards on any high-resolution screen, and the
 * automatic guess never got another chance because a stored value had appeared. The original is
 * captured on first use and put back when the test JVM exits.
 *
 * <p>The polling this class used to do is gone: {@code PrefMonitorDouble.set()} now updates its own
 * cached value, so a write is visible immediately.
 */
final class UiScaleTestSupport {

  private static boolean captured;
  private static Double original;

  private UiScaleTestSupport() {
    // Utility class, not instantiable.
  }

  /** Sets the scale factor, remembering what the developer had before the first change. */
  static synchronized void setScaleFactor(double scale) {
    if (!captured) {
      final var stored = AppPreferences.getPrefs().getDouble("Scale", Double.NaN);
      original = Double.isNaN(stored) ? null : stored;
      captured = true;
      Runtime.getRuntime().addShutdownHook(new Thread(UiScaleTestSupport::restore));
    }
    AppPreferences.SCALE_FACTOR.set(scale);
  }

  /** Puts the developer's own scale back, or removes the key if they never had one. */
  private static void restore() {
    final var prefs = AppPreferences.getPrefs();
    if (original == null) {
      prefs.remove("Scale");
    } else {
      prefs.putDouble("Scale", original);
    }
    try {
      prefs.flush();
    } catch (java.util.prefs.BackingStoreException ignored) {
      // Nothing useful to do while the process is going away.
    }
  }
}
