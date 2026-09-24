/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.io;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class DotMatrixDrawingTest {
  @Test
  void scaledCircularDotsRemainInsideTheirPositionedCell() {
    for (final var scale : new int[] {1, 2, 3}) {
      final var matrix = new DotMatrix();
      matrix.setScaleX(scale);
      matrix.setScaleY(scale);
      final var image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
      final var g = image.createGraphics();
      g.setColor(Color.RED);
      matrix.drawCircle(g, 80, 50);
      g.dispose();
      var painted = 0;
      for (var y = 0; y < image.getHeight(); y++) {
        for (var x = 0; x < image.getWidth(); x++) {
          if ((image.getRGB(x, y) >>> 24) == 0) continue;
          painted++;
          assertTrue(x >= 80 + scale && x < 80 + 9 * scale, "dot x outside cell: " + x);
          assertTrue(y >= 50 + scale && y < 50 + 9 * scale, "dot y outside cell: " + y);
        }
      }
      assertTrue(painted > 0);
    }
  }
}
