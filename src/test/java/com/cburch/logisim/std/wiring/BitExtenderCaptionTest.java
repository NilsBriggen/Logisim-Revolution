/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.wiring;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.ComponentCaption;
import com.cburch.logisim.tools.ToolTipMaker;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class BitExtenderCaptionTest {
  @Test
  void everyModeKeepsFullWidthsPortsAndDocumentUnitCaptionSizes() {
    for (final var mode : List.of("zero", "one", "sign", "input")) {
      final var attrs = BitExtender.FACTORY.createAttributeSet();
      attrs.setValue(BitExtender.ATTR_TYPE, BitExtender.ATTR_TYPE.parse(mode));
      for (final var attr : attrs.getAttributes()) {
        if (attr.getName().equals("in_width") || attr.getName().equals("out_width")) {
          set(attrs, attr, "64");
        }
      }
      final var comp = BitExtender.FACTORY.createComponent(Location.create(60, 40, true), attrs);
      final var bounds = comp.getBounds();
      final var ends = List.copyOf(comp.getEnds());
      final var small = paint(comp, 12);
      final var large = paint(comp, 32);
      assertArrayEquals(small.getRGB(0, 0, 100, 80, null, 0, 100),
          large.getRGB(0, 0, 100, 80, null, 0, 100));
      final var g = small.createGraphics();
      try {
        final var fm = g.getFontMetrics(ComponentCaption.FONT.deriveFont(10f));
        assertEquals("64→64", ComponentCaption.fit(fm, "64→64", 34),
            "critical widths must not be abbreviated");
      } finally {
        g.dispose();
      }
      final var tooltip = (ToolTipMaker) comp.getFeature(ToolTipMaker.class);
      assertTrue(tooltip.getToolTip(new ComponentUserEvent(null, 40, 40)).contains("64→64"));
      assertEquals(bounds, comp.getBounds());
      assertEquals(ends, comp.getEnds());
      assertEquals(mode, attrs.getValue(BitExtender.ATTR_TYPE).getValue());
    }
  }

  private static <T> void set(AttributeSet attrs, Attribute<T> attr, String text) {
    attrs.setValue(attr, attr.parse(text));
  }

  private static BufferedImage paint(Component comp, int inheritedSize) {
    final var image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_ARGB);
    final var g = image.createGraphics();
    try {
      g.setFont(new Font(Font.SERIF, Font.BOLD, inheritedSize));
      final var context = new ComponentDrawContext(null, null, null, g, g);
      context.setShowState(false);
      comp.draw(context);
    } finally {
      g.dispose();
    }
    return image;
  }
}
