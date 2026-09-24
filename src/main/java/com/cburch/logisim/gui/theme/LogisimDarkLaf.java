/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.theme;

import com.formdev.flatlaf.FlatDarkLaf;

/**
 * The application's dark theme. See {@link LogisimLightLaf} for why this is a class.
 */
public class LogisimDarkLaf extends FlatDarkLaf {

  private static final long serialVersionUID = 1L;

  public static final String NAME = "Logisim Dark";

  public static boolean setup() {
    return setup(new LogisimDarkLaf());
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return "Logisim Revolution dark theme";
  }
}
