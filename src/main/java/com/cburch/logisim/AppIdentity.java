/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim;

import java.io.File;

/** Paths that distinguish Revolution from an Evolution installation on the same computer. */
public final class AppIdentity {
  public static final String NAME = "Logisim Revolution";
  public static final String PREFERENCES_PATH = "/dev/briggen/logisimrevolution";
  public static final String EVOLUTION_PREFERENCES_PATH = "/com/cburch/logisim";

  private AppIdentity() {
    throw new UnsupportedOperationException("Utility class, not instantiable");
  }

  /** Unnamed recovery files belong to this application alone. */
  public static File unnamedRecoveryDirectory() {
    return new File(new File(System.getProperty("user.home"), ".logisim-revolution"), "recovery");
  }
}
