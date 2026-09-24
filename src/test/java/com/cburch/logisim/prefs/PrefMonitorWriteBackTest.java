/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.prefs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A preference must report the value it was just given.
 *
 * <p>Two of the five monitor types wrote the new value to the store and left their own cached copy
 * alone, so the value only caught up when the preference-change listener happened to fire on
 * another thread. The visible effect was the interface scale: the monitor was built with the
 * automatic guess, its constructor wrote the stored value back without updating the cache, and the
 * two then disagreed — the program reported one size and the store held another. Every boolean
 * preference in the program behaved the same way.
 */
class PrefMonitorWriteBackTest {

  private static final String DOUBLE_KEY = "testScaleWriteBack";
  private static final String BOOLEAN_KEY = "testFlagWriteBack";

  @AfterEach
  void forgetTestKeys() {
    AppPreferences.getPrefs().remove(DOUBLE_KEY);
    AppPreferences.getPrefs().remove(BOOLEAN_KEY);
  }

  @Test
  void doubleReportsWhatItWasJustGiven() {
    final var monitor = new PrefMonitorDouble(DOUBLE_KEY, 1.6);
    monitor.set(2.25);
    assertEquals(2.25, monitor.get(), "read back before the store's listener could fire");
    assertEquals(2.25, AppPreferences.getPrefs().getDouble(DOUBLE_KEY, Double.NaN));
  }

  @Test
  void booleanReportsWhatItWasJustGiven() {
    final var monitor = new PrefMonitorBoolean(BOOLEAN_KEY, false);
    monitor.set(true);
    assertEquals(true, monitor.get(), "read back before the store's listener could fire");
    assertEquals(true, AppPreferences.getPrefs().getBoolean(BOOLEAN_KEY, false));
  }

  /**
   * The case that broke the interface scale: a value already in the store, and a monitor whose
   * default differs from it. The monitor must agree with the store, not with its own default.
   */
  @Test
  void storedValueWinsOverTheDefault() {
    AppPreferences.getPrefs().putDouble(DOUBLE_KEY, 1.0);
    final var monitor = new PrefMonitorDouble(DOUBLE_KEY, 1.6);
    assertEquals(1.0, monitor.get());
  }

  @Test
  void absentValueLeavesTheDefaultInPlaceAndUnwritten() {
    final var monitor = new PrefMonitorDouble(DOUBLE_KEY, 1.6);
    assertEquals(1.6, monitor.get());
    assertEquals(
        Double.NaN,
        AppPreferences.getPrefs().getDouble(DOUBLE_KEY, Double.NaN),
        "a default the user never chose should not be written to the store");
  }

  /** The automatic guess has to survive being read, which is what stopped happening. */
  @Test
  void theInterfaceScaleIsNeverZeroOrNegative() {
    assertEquals(true, AppPreferences.SCALE_FACTOR.get() > 0.0);
    assertEquals(true, AppPreferences.getAutoScaleFactor() >= 1.0);
  }
}
