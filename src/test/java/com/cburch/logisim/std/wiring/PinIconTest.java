/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.wiring;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.tools.AddTool;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/**
 * The pin tool icons carry their value, like the pins on the canvas, and put the connection on the
 * side a wire attaches: that is what tells them apart from the gate icons next to them.
 */
class PinIconTest {

  private static BufferedImage paint(boolean output) {
    final var tool = new AddTool(Pin.FACTORY);
    tool.getAttributeSet().setValue(Pin.ATTR_TYPE, output ? Pin.OUTPUT : Pin.INPUT);
    tool.getAttributeSet()
        .setValue(ProbeAttributes.PROBEAPPEARANCE, ProbeAttributes.APPEAR_EVOLUTION_NEW);
    final var size = AppPreferences.getIconSize();
    final var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    final var g = image.createGraphics();
    try {
      g.setColor(Color.BLACK);
      final var context = new ComponentDrawContext(new JPanel(), null, null, g, g);
      tool.paintIcon(context, 0, 0);
    } finally {
      g.dispose();
    }
    return image;
  }

  private static boolean greenIn(BufferedImage image, int fromX, int toX) {
    final var green = Value.TRUE.getColor().getRGB() & 0xffffff;
    for (var x = fromX; x < toX; x++) {
      for (var y = 0; y < image.getHeight(); y++) {
        final var argb = image.getRGB(x, y);
        if ((argb >>> 24) > 0x80 && (argb & 0xffffff) == green) return true;
      }
    }
    return false;
  }

  @Test
  void inputIconHasItsValueAndItsStubOnTheRight() {
    final var image = paint(false);
    final var size = image.getWidth();
    assertTrue(greenIn(image, size - 2, size), "connection stub after the point");
    assertFalse(greenIn(image, 0, 2), "nothing attached on the left");
    assertTrue(greenIn(image, size / 4, size / 2), "value inside the tag");
  }

  @Test
  void outputIconHasItsStubOnTheLeft() {
    final var image = paint(true);
    final var size = image.getWidth();
    assertTrue(greenIn(image, 0, 2), "connection stub before the flat end");
    assertFalse(greenIn(image, size - 2, size), "the point is free");
  }
}
