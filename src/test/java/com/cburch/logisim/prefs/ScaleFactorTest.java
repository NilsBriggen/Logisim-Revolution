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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.TestBase;
import java.awt.GraphicsEnvironment;
import org.junit.jupiter.api.Test;

/** Tests how the default interface scale is chosen. */
public class ScaleFactorTest extends TestBase {

  /**
   * The old heuristic divided the screen height by a thousand and applied that on top of whatever
   * the platform already did, which double-scaled the interface on a scaled display.
   */
  @Test
  public void autoScaleDefersToThePlatformWhenItAlreadyScales() {
    if (GraphicsEnvironment.isHeadless()) return;
    if (AppPreferences.getPlatformScaleFactor() > 1.0) {
      assertEquals(1.0, AppPreferences.getAutoScaleFactor());
    }
  }

  @Test
  public void platformScaleIsAlwaysUsable() {
    final var scale = AppPreferences.getPlatformScaleFactor();
    assertTrue(scale > 0.0, "platform scale must be positive, was " + scale);
  }

  /** A scale of zero or a negative one would collapse every scaled dimension. */
  @Test
  public void autoScaleIsNeverBelowOne() {
    assertTrue(AppPreferences.getAutoScaleFactor() >= 1.0);
  }

  @Test
  public void headlessRunsUseUnitScale() {
    if (!GraphicsEnvironment.isHeadless()) return;
    assertEquals(1.0, AppPreferences.getAutoScaleFactor());
  }

  /** The migration compares against this, so it has to keep reproducing the old behaviour. */
  @Test
  public void legacyAutoScaleStillMatchesTheOldFormula() {
    if (GraphicsEnvironment.isHeadless()) {
      assertEquals(0.0, AppPreferences.getLegacyAutoScaleFactor());
      return;
    }
    final var height =
        java.awt.Toolkit.getDefaultToolkit().getScreenSize().getHeight();
    assertEquals(height / 1000.0, AppPreferences.getLegacyAutoScaleFactor());
  }
}
