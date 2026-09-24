/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class CanvasStyleContrastTest {
  @Test
  void choosesHigherWcagContrastWithoutChangingTheFill() {
    assertEquals(Color.BLACK, CanvasStyle.contrastInk(Color.GREEN));
    assertEquals(Color.WHITE, CanvasStyle.contrastInk(new Color(0x004000)));
    assertEquals(Color.BLACK, CanvasStyle.contrastInk(new Color(0x777777)));
    assertEquals(Color.BLACK, CanvasStyle.contrastInk(Color.WHITE));
    assertEquals(Color.WHITE, CanvasStyle.contrastInk(Color.BLACK));
    assertEquals(Color.WHITE, CanvasStyle.contrastInk(Color.BLUE));
    final var fill = new Color(0x36E952);
    CanvasStyle.contrastInk(fill);
    assertEquals(0xFF36E952, fill.getRGB());
  }
}

