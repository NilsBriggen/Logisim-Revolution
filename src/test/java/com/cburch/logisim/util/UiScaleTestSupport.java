/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import static org.junit.jupiter.api.Assertions.fail;

import com.cburch.logisim.prefs.AppPreferences;

/**
 * Helper for tests that need to change the interface scale factor.
 *
 * <p>{@code PrefMonitorDouble.set()} writes to the backing preference node but leaves its cached
 * value untouched until the {@link java.util.prefs.Preferences} listener fires, and that callback is
 * delivered on another thread. Reading the preference straight after writing it therefore still sees
 * the previous value, so tests have to wait for the change to propagate.
 */
final class UiScaleTestSupport {

  /** How long to wait for a preference change to reach the monitor before giving up. */
  private static final long TIMEOUT_MILLIS = 5_000;

  private UiScaleTestSupport() {
    // Utility class, not instantiable.
  }

  /** Sets the scale factor and blocks until the change is visible, failing the test on timeout. */
  static void setScaleFactor(double scale) {
    AppPreferences.SCALE_FACTOR.set(scale);
    final var deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
    while (AppPreferences.SCALE_FACTOR.get() != scale) {
      if (System.currentTimeMillis() > deadline) {
        fail("scale factor " + scale + " was not applied within " + TIMEOUT_MILLIS + "ms");
      }
      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        fail("interrupted while waiting for the scale factor to be applied");
      }
    }
  }
}
