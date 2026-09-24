/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.theme;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * The application's light theme.
 *
 * <p>A real look-and-feel class rather than a loose set of properties, so the theme has a stable
 * name to store in the preferences and FlatLaf can pick up
 * {@code LogisimLightLaf.properties} next to it.
 */
public class LogisimLightLaf extends FlatLightLaf {

  private static final long serialVersionUID = 1L;

  public static final String NAME = "Logisim Light";

  public static boolean setup() {
    return setup(new LogisimLightLaf());
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return "Logisim Revolution light theme";
  }
}
