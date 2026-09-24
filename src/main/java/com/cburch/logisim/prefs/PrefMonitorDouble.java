/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.prefs;

import java.util.prefs.PreferenceChangeEvent;

/**
 * Represents a preference monitor for double values. This class listens
 * to preference changes and appropriately updates its internal value.
 */
class PrefMonitorDouble extends AbstractPrefMonitor<Double> {
  /** Default double value for this preference monitor. */
  private final double dflt;

  /** Current double value of this preference monitor. */
  private double value;

  /**
   * Constructs a new preference monitor for double values.
   *
   * @param name The name of the preference.
   * @param dflt The default double value.
   */
  public PrefMonitorDouble(String name, double dflt) {
    super(name);
    this.dflt = dflt;
    this.value = dflt;
    final var prefs = AppPreferences.getPrefs();
    set(prefs.getDouble(name, dflt));
    prefs.addPreferenceChangeListener(this);
  }

  /**
   * Retrieves the current value of the preference.
   *
   * @return The current double value.
   */
  public Double get() {
    return value;
  }

  /**
   * Handles preference changes and updates the internal value if needed.
   * Does nothing if newValue is the same as the current value.
   *
   * @param event The event indicating a preference change.
   */
  public void preferenceChange(PreferenceChangeEvent event) {
    final var prefs = event.getNode();
    final var prop = event.getKey();
    final var name = getIdentifier();
    if (prop.equals(name)) {
      final var oldValue = value;
      final var newValue = prefs.getDouble(name, dflt);
      if (newValue != oldValue) {
        value = newValue;
        AppPreferences.firePropertyChange(name, oldValue, newValue);
      }
    }
  }

  /**
   * Sets the preference value.
   * Does nothing if newValue is the same as the current value.
   *
   * @param newValue The new double value to set.
   */
  public void set(Double newValue) {
    final var oldValue = value;
    final var newVal = newValue;
    if (oldValue != newVal) {
      // The cached value is updated here rather than being left to the preference-change
      // listener, which runs on another thread. Without this the monitor kept answering with the
      // value it was constructed with until that listener happened to fire -- and, worse, the
      // constructor's own `set(stored)` wrote the stored value back while leaving the cache on
      // the default, so the interface scale reported one number and the store held another.
      value = newVal;
      AppPreferences.getPrefs().putDouble(getIdentifier(), newVal);
      AppPreferences.firePropertyChange(getIdentifier(), oldValue, newVal);
    }
  }
}
