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
 * An attribute set standing for several things at once, which may disagree.
 *
 * <p>Selecting four gates and looking at the properties panel shows one row per property they all
 * have. When they do not all have the same value there is nothing sensible to put in the cell, and
 * the panel used to put nothing there at all — indistinguishable from a property that is genuinely
 * empty, and from one the program failed to read.
 *
 * <p>A set that can be in that position says so here, so the panel can write it down.
 */
public interface VariousValues {

  /** Whether the things this set stands for disagree about {@code attr}. */
  boolean isVarious(Attribute<?> attr);
}
