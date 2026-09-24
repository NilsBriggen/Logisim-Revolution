/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.gates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class GateLabelLayoutTest {
  @Test
  void longGateLabelsAreOutsideWithoutMovingSymbolsOrPorts() {
    for (final var factory : List.of(AndGate.FACTORY, OrGate.FACTORY, XorGate.FACTORY,
        NandGate.FACTORY, NotGate.FACTORY, Buffer.FACTORY, ControlledBuffer.FACTORY_BUFFER)) {
      for (final var facing : List.of(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH)) {
        final var attrs = factory.createAttributeSet();
        attrs.setValue(StdAttr.FACING, facing);
        attrs.setValue(StdAttr.LABEL, "long_gate_label_0123456789");
        final var font = new Font(Font.SERIF, Font.BOLD, 24);
        attrs.setValue(StdAttr.LABEL_FONT, font);
        final var comp = factory.createComponent(Location.create(250, 150, true), attrs);
        final var body = comp.getBounds();
        final var ends = List.copyOf(comp.getEnds());
        final var g = new BufferedImage(1000, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
          final var painted = comp.getBounds(g);
          assertFalse(body.equals(painted), factory.getName() + " " + facing);
          assertTrue(painted.contains(body));
          final var context = new ComponentDrawContext(null, null, null, g, g);
          context.setShowState(false);
          comp.draw(context);
          assertEquals(painted, comp.getBounds(g));
          assertEquals(body, comp.getBounds());
          assertEquals(ends, comp.getEnds());
          assertEquals(font, attrs.getValue(StdAttr.LABEL_FONT));
          assertEquals("long_gate_label_0123456789", attrs.getValue(StdAttr.LABEL));
        } finally {
          g.dispose();
        }
      }
    }
  }
}
