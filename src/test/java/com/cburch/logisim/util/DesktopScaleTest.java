/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DesktopScaleTest {
  @Test
  void suppliesDesktopScaleMissingFromJava2dAndFlatLaf() {
    assertEquals(1.5, DesktopScale.effectiveFactor(null, 1.0, 1.0, 144.0));
  }

  @Test
  void doesNotMultiplyDpiAlreadyAccountedForByDeviceOrNativeFonts() {
    assertEquals(1.0, DesktopScale.effectiveFactor(null, 1.0, 1.5, 144.0));
    assertEquals(1.0, DesktopScale.effectiveFactor(null, 1.0, 2.0, 144.0));
    assertEquals(1.5, DesktopScale.effectiveFactor(null, 1.5, 1.0, 144.0));
  }

  @Test
  void missingOrInvalidDpiLeavesNativeScaleAlone() {
    for (final var dpi : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, 0, 100_000}) {
      assertEquals(1.25, DesktopScale.effectiveFactor(null, 1.25, 1.0, dpi));
    }
    assertEquals(1.0, DesktopScale.effectiveFactor(null, 1.0, 1.0, Double.NaN));
  }

  @Test
  void explicitScaleWinsIndependentlyOfDesktopAndDevice() {
    assertEquals(1.6, DesktopScale.effectiveFactor(1.6, 1.0, 1.0, 144.0));
    assertEquals(1.6, DesktopScale.effectiveFactor(1.6, 2.0, 2.0, 192.0));
    assertEquals(1.0, DesktopScale.effectiveFactor(1.0, 1.5, 1.0, 144.0));
  }

  @Test
  void understandsAdvertisedXftFormatsAndRejectsUnrelatedResources() {
    assertEquals(144.0, DesktopScale.parseDpi(144 * 1024, true));
    assertEquals(144.0, DesktopScale.parseDpi("144", false));
    assertEquals(144.0, DesktopScale.parseXResources("Xcursor.size:\t36\nXft.dpi:\t144\n"));
    assertTrue(Double.isNaN(DesktopScale.parseXResources("Xcursor.size:\t144")));
    assertTrue(Double.isNaN(DesktopScale.parseXResources("Xft.dpi: invalid")));
    assertTrue(Double.isNaN(DesktopScale.parseDpi(-1, true)));
    assertTrue(Double.isNaN(DesktopScale.parseDpi(null, false)));
  }
}
