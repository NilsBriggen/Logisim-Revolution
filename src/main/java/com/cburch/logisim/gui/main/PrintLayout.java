/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

/**
 * Where a circuit goes on a printed page: the scale it is drawn at and whether it is turned a
 * quarter turn to make better use of the paper.
 *
 * <p>Printing and the print preview both take their layout from here, so the preview shows the
 * page exactly as it is printed.
 *
 * @param scale page units (points) per circuit unit
 * @param rotated whether the circuit is turned 90 degrees on the page
 * @param fits whether the whole circuit lies on the page at that scale
 */
record PrintLayout(double scale, boolean rotated, boolean fits) {

  /** Rotation must enlarge the circuit by at least this factor to be worth doing. */
  static final double ROTATE_GAIN = 1.1;

  /** A {@code fixedScale} of zero or less: scale the circuit up or down to fill the page. */
  static final double FIT_TO_PAGE = 0;

  /** Tolerance for rounding when deciding whether a circuit fits. */
  private static final double EPSILON = 1e-9;

  /**
   * Lays one circuit out on a page.
   *
   * @param pageWidth width of the imageable area of the page, in points
   * @param pageHeight height of the imageable area, in points
   * @param circuitWidth width of the circuit's bounds, in circuit units
   * @param circuitHeight height of the circuit's bounds, in circuit units
   * @param headerHeight height reserved above the circuit for the header line, in points
   * @param rotateToFit whether the circuit may be turned when that makes it larger
   * @param fixedScale the scale to print at, or {@link #FIT_TO_PAGE} to fill the page
   */
  static PrintLayout compute(
      double pageWidth,
      double pageHeight,
      double circuitWidth,
      double circuitHeight,
      double headerHeight,
      boolean rotateToFit,
      double fixedScale) {
    final var width = Math.max(1.0, circuitWidth);
    final var height = Math.max(1.0, circuitHeight);
    final var fit = Math.min(pageWidth / width, (pageHeight - headerHeight) / height);
    // Turned, the page's height carries the circuit's width, and the header still sits above.
    final var fitRotated = Math.min(pageHeight / width, (pageWidth - headerHeight) / height);
    if (fixedScale > 0) {
      // At a fixed size, turning only helps a circuit that would not fit upright but does turned.
      final var rotate = rotateToFit && fixedScale > fit && fitRotated > fit;
      final var room = rotate ? fitRotated : fit;
      return new PrintLayout(fixedScale, rotate, fixedScale <= room + EPSILON);
    }
    // The circuit is scaled to fill the page either way; turning it is only worth it for one
    // that would otherwise have to be shrunk noticeably and gains noticeably from the turn.
    if (rotateToFit && fit < 1.0 / ROTATE_GAIN && fitRotated >= fit * ROTATE_GAIN) {
      return new PrintLayout(fitRotated, true, true);
    }
    return new PrintLayout(fit, false, true);
  }
}
