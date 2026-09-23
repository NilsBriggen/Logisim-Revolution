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
 * Represents a preference monitor for integer values. This class listens
 * to preference changes and appropriately updates its internal value.
 */
class PrefMonitorInt extends AbstractPrefMonitor<Integer> {
  /** Default integer value for this preference monitor. */
  private final int dflt;

  /** Current integer value of this preference monitor. */
  private int value;

  /**
   * Constructs a new preference monitor for integer values.
   *
   * @param name The name of the preference.
   * @param dflt The default integer value.
   */
  public PrefMonitorInt(String name, int dflt) {
    super(name);
    this.dflt = dflt;
    this.value = dflt;
    final var prefs = AppPreferences.getPrefs();
    set(prefs.getInt(name, dflt));
    prefs.addPreferenceChangeListener(this);
  }

  /**
   * Retrieves the current value of the preference.
   *
   * @return The current integer value.
   */
  public Integer get() {
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
      final var newValue = prefs.getInt(name, dflt);
      if (newValue != oldValue) {
        value = newValue;
        AppPreferences.firePropertyChangeFromStore(name, oldValue, newValue);
      }
    }
  }

  /**
   * Sets the preference value.
   * Does nothing if newValue is the same as the current value.
   *
   * @param newValue The new integer value to set.
   */
  public void set(Integer newValue) {
    final var oldValue = value;
    final var newVal = newValue;
    if (oldValue != newVal) {
      // The cached value is updated here rather than being left to the preference-change
      // listener, which runs on another thread: a caller that sets a colour and then reads it
      // back, as switching theme does, would otherwise get the colour it just replaced.
      value = newVal;
      AppPreferences.getPrefs().putInt(getIdentifier(), newVal);
      AppPreferences.firePropertyChange(getIdentifier(), oldValue, newVal);
    }
  }
}
