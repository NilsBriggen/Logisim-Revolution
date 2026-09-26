/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Dimension;
import org.junit.jupiter.api.Test;

/** The K-map grows with the room it is given (a maximised window) instead of staying tiny. */
class KarnaughMapZoomTest {

  @Test
  void fillsTheRoomKeepingItsProportions() {
    assertEquals(2.0, KarnaughMapPanel.zoomFor(new Dimension(100, 50), new Dimension(400, 100)));
    assertEquals(3.0, KarnaughMapPanel.zoomFor(new Dimension(100, 50), new Dimension(300, 400)));
  }

  @Test
  void neverShrinksAndStopsAtTheLimit() {
    assertEquals(1.0, KarnaughMapPanel.zoomFor(new Dimension(100, 50), new Dimension(80, 40)));
    assertEquals(
        KarnaughMapPanel.MAX_ZOOM,
        KarnaughMapPanel.zoomFor(new Dimension(10, 10), new Dimension(4000, 4000)));
    assertEquals(1.0, KarnaughMapPanel.zoomFor(new Dimension(0, 0), new Dimension(400, 400)));
  }
}
