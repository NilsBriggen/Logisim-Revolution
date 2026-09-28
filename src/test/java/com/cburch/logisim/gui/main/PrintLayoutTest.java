/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PrintLayoutTest {

  /** The imageable area of a Letter page with one-inch margins, in points. */
  private static final double PAGE_WIDTH = 468;

  private static final double PAGE_HEIGHT = 648;

  private static final double EPSILON = 1e-9;

  @Test
  void fitToPageScalesASmallCircuitUp() {
    final var layout =
        PrintLayout.compute(PAGE_WIDTH, PAGE_HEIGHT, 100, 50, 0, true, PrintLayout.FIT_TO_PAGE);

    assertEquals(4.68, layout.scale(), EPSILON);
    assertFalse(layout.rotated());
    assertTrue(layout.fits());
  }

  @Test
  void fitToPageScalesALargeCircuitDown() {
    final var layout =
        PrintLayout.compute(PAGE_WIDTH, PAGE_HEIGHT, 936, 648, 0, false, PrintLayout.FIT_TO_PAGE);

    assertEquals(0.5, layout.scale(), EPSILON);
    assertFalse(layout.rotated());
    assertTrue(layout.fits());
  }

  @Test
  void headerLineReducesTheHeightAvailable() {
    final var layout =
        PrintLayout.compute(PAGE_WIDTH, PAGE_HEIGHT, 100, 638, 10, false, PrintLayout.FIT_TO_PAGE);

    assertEquals(1.0, layout.scale(), EPSILON);
  }

  @Test
  void wideCircuitTooLargeForThePageIsTurned() {
    final var layout =
        PrintLayout.compute(PAGE_WIDTH, PAGE_HEIGHT, 1296, 468, 0, true, PrintLayout.FIT_TO_PAGE);

    // Upright it would print at 468 / 1296; turned the page's height carries its width.
    assertTrue(layout.rotated());
    assertEquals(0.5, layout.scale(), EPSILON);
  }

  @Test
  void circuitIsNotTurnedWhenRotationIsOff() {
    final var layout =
        PrintLayout.compute(PAGE_WIDTH, PAGE_HEIGHT, 1296, 468, 0, false, PrintLayout.FIT_TO_PAGE);

    assertFalse(layout.rotated());
    assertEquals(PAGE_WIDTH / 1296, layout.scale(), EPSILON);
  }

  @Test
  void circuitThatFitsAtFullSizeIsNotTurned() {
    // Wide, but small enough to print upright without shrinking.
    final var layout =
        PrintLayout.compute(PAGE_WIDTH, PAGE_HEIGHT, 400, 100, 0, true, PrintLayout.FIT_TO_PAGE);

    assertFalse(layout.rotated());
    assertEquals(1.17, layout.scale(), EPSILON);
  }

  @Test
  void turningIsSkippedWhenItGainsLessThanTenPercent() {
    // Nearly square: turning cannot enlarge it noticeably.
    final var layout =
        PrintLayout.compute(PAGE_WIDTH, PAGE_HEIGHT, 1000, 1000, 0, true, PrintLayout.FIT_TO_PAGE);

    assertFalse(layout.rotated());
    assertEquals(0.468, layout.scale(), EPSILON);
  }

  @Test
  void fixedScaleIsKeptAndReportsWhetherTheCircuitFits() {
    final var fits = PrintLayout.compute(PAGE_WIDTH, PAGE_HEIGHT, 200, 100, 0, true, 2.0);
    assertEquals(2.0, fits.scale(), EPSILON);
    assertFalse(fits.rotated());
    assertTrue(fits.fits());

    final var tooLarge = PrintLayout.compute(PAGE_WIDTH, PAGE_HEIGHT, 200, 100, 0, true, 4.0);
    assertEquals(4.0, tooLarge.scale(), EPSILON);
    assertFalse(tooLarge.fits());
  }

  @Test
  void fixedScaleTurnsACircuitThatOnlyFitsTurned() {
    // 600 wide does not fit the 468-point width, but fits the 648-point height when turned.
    final var turned = PrintLayout.compute(PAGE_WIDTH, PAGE_HEIGHT, 600, 200, 0, true, 1.0);
    assertTrue(turned.rotated());
    assertTrue(turned.fits());

    final var upright = PrintLayout.compute(PAGE_WIDTH, PAGE_HEIGHT, 600, 200, 0, false, 1.0);
    assertFalse(upright.rotated());
    assertFalse(upright.fits());
  }
}
