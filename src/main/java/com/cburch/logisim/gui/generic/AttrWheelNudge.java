/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.BoundedAttribute;
import java.util.Locale;
import java.util.Optional;

/**
 * Works out the new value when the wheel is turned over a numeric property.
 *
 * <p>Separated from the table so the rule can be read and tested on its own. The rule used to live
 * inside the mouse handler and decided whether a value could go below one by asking whether the
 * property's *translated* name contained the word "width" — so it worked in English and in no
 * other language, and it clamped unrelated properties whose German or French name happened to
 * contain that substring. The limits now come from the property itself, through {@link
 * BoundedAttribute}.
 */
public final class AttrWheelNudge {
  private static final String HEX_PREFIX = "0x";

  private AttrWheelNudge() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /**
   * The value the property should take after the wheel has been turned.
   *
   * @param displayValue the value as the table is showing it, decimal or {@code 0x}-prefixed hex
   * @param clicks wheel rotation, negative for away from the user, as AWT reports it
   * @param attribute the property being nudged, used for its limits; may be {@code null}
   * @return the new value written the same way the old one was, or empty when the value is not a
   *     number, the wheel did not move, or the limits leave it where it is
   */
  public static Optional<String> nudge(String displayValue, int clicks, Attribute<?> attribute) {
    if (displayValue == null || clicks == 0) return Optional.empty();
    final var text = displayValue.trim();
    if (text.isEmpty()) return Optional.empty();

    final var hex = text.regionMatches(true, 0, HEX_PREFIX, 0, HEX_PREFIX.length());
    final long current;
    try {
      current = hex ? Long.parseLong(text.substring(2), 16) : Long.parseLong(text);
    } catch (NumberFormatException notANumber) {
      return Optional.empty();
    }

    // Turning the wheel away from the user reports a negative rotation and should raise the value.
    long next;
    try {
      next = Math.subtractExact(current, (long) clicks);
    } catch (ArithmeticException overflow) {
      next = clicks < 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
    }
    if (attribute instanceof BoundedAttribute bounded) {
      next = Math.max(bounded.minValue(), Math.min(bounded.maxValue(), next));
    }
    if (next == current) return Optional.empty();

    return Optional.of(
        hex ? HEX_PREFIX + Long.toHexString(next).toUpperCase(Locale.ROOT) : Long.toString(next));
  }
}
