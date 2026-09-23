/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LayoutPrefsTest {

  @Test
  void panelSizeIsAlwaysUsableAndNeverWiderThanAWindow() {
    assertEquals(LayoutPrefs.MIN_PANEL, LayoutPrefs.clamp(0));
    assertEquals(LayoutPrefs.MIN_PANEL, LayoutPrefs.clamp(-40));
    assertEquals(LayoutPrefs.MAX_PANEL, LayoutPrefs.clamp(10_000));
    assertEquals(300, LayoutPrefs.clamp(300));
  }

  @Test
  void theShippedSizesAreWithinTheAllowedRange() {
    for (final var size :
        new int[] {
          LayoutPrefs.DEFAULT_SIDE_WIDTH,
          LayoutPrefs.DEFAULT_INSPECTOR_WIDTH,
          LayoutPrefs.DEFAULT_BOTTOM_HEIGHT
        }) {
      assertEquals(size, LayoutPrefs.clamp(size), "default outside the clamp range: " + size);
    }
  }

  @Test
  void theRangeLeavesRoomForBothSidePanelsOnASmallScreen() {
    // Two panels at their smallest plus a usable canvas has to fit a 1024-wide window, or the
    // shipped layout is unusable on a laptop.
    assertTrue(2 * LayoutPrefs.MIN_PANEL + 400 <= 1024,
        "minimum panels leave too little for the canvas");
  }
}
