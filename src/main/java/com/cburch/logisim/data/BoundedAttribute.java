/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.data;

/**
 * An attribute whose value is a number with a smallest and a largest allowed value.
 *
 * <p>Several attributes have always known their own limits privately — a bit width runs from 1 to
 * 64, a clock duration has a minimum tick count — but nothing outside them could ask. The user
 * interface therefore had to guess, and it guessed by looking at the attribute's *translated* name
 * for the word "width", which worked in English and nowhere else.
 *
 * <p>Implementing this says the limits out loud so a spinner, a nudge or a validator can honour
 * them without knowing which attribute it is holding.
 */
public interface BoundedAttribute {

  /** The smallest value this attribute will accept. */
  int minValue();

  /** The largest value this attribute will accept. */
  int maxValue();
}
