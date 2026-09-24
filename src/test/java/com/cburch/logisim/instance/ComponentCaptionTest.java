/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.data.Bounds;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ComponentCaptionTest {
  @Test
  void unicodePreviewsFitWithoutChangingFontsOrLeakingInk() {
    final var image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
    final var g = image.createGraphics();
    try {
      final var original = new Font(Font.SERIF, Font.BOLD, 28);
      g.setFont(original);
      g.setColor(Color.RED);
      final var fm = g.getFontMetrics(ComponentCaption.FONT);
      final var text = "Data_🔬_long_bus_name_with_non_ascii_äöü";
      final var fitted = ComponentCaption.fit(fm, text, 70);
      assertTrue(fm.stringWidth(fitted) <= 70);
      assertTrue(fitted.endsWith("…"));
      assertFalse(fitted.chars().anyMatch(c -> c == 0xFFFD));
      assertEquals("short", ComponentCaption.fit(fm, "short", 100));
      assertEquals("", ComponentCaption.fit(fm, text, 0));
      final var bounds = Bounds.create(20, 30, 74, 16);
      ComponentCaption.draw(g, text, bounds, ComponentCaption.FONT);
      assertEquals(original, g.getFont());
      assertEquals(Color.RED, g.getColor());
      var ink = 0;
      for (var y = 0; y < image.getHeight(); y++) {
        for (var x = 0; x < image.getWidth(); x++) {
          if ((image.getRGB(x, y) >>> 24) == 0) continue;
          ink++;
          assertTrue(bounds.contains(x, y));
        }
      }
      assertTrue(ink > 10);
    } finally {
      g.dispose();
    }
  }
}
