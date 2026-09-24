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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class GateGhostPaintTest {
  @Test
  void ieeeAndIecGhostsUseSuppliedInkForEveryFacingAndNegation() {
    for (final var factory : List.of(AndGate.FACTORY, NandGate.FACTORY, OrGate.FACTORY,
        NorGate.FACTORY, XorGate.FACTORY, XnorGate.FACTORY)) {
      for (final var shape :
          List.of(AppPreferences.SHAPE_SHAPED, AppPreferences.SHAPE_RECTANGULAR)) {
        for (final var facing :
            List.of(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH)) {
          for (final var negateInput : new boolean[] {false, true}) {
            final var attrs = factory.createAttributeSet();
            attrs.setValue(StdAttr.FACING, facing);
            attrs.setValue(new NegateAttribute(0, null), negateInput);
            final var bounds = factory.getOffsetBounds(attrs);
            for (final var zoom : new int[] {1, 2}) {
              final var image = new BufferedImage(220 * zoom, 220 * zoom,
                  BufferedImage.TYPE_INT_ARGB);
              final var g = image.createGraphics();
              final var ink = new Color(0x5689A3);
              try {
                g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
                g.scale(zoom, zoom);
                final var transform = g.getTransform();
                final var context = new ComponentDrawContext(null, null, null, g, g) {
                  @Override
                  public Object getGateShape() {
                    return shape;
                  }
                };
                factory.drawGhost(context, ink, 110, 110, attrs);
                assertEquals(transform, g.getTransform(), "ghost moved later painters");
                assertEquals(ink, g.getColor(), "ghost replaced supplied ink");
                assertEquals(bounds, factory.getOffsetBounds(attrs));
                assertEquals(facing, attrs.getValue(StdAttr.FACING));
                assertEquals(negateInput,
                    attrs.getValue(new NegateAttribute(0, null)).booleanValue());
              } finally {
                g.dispose();
              }
              var count = 0;
              for (var y = 0; y < image.getHeight(); y++) {
                for (var x = 0; x < image.getWidth(); x++) {
                  final var pixel = image.getRGB(x, y);
                  if ((pixel >>> 24) == 0) continue;
                  count++;
                  assertEquals(ink.getRGB(), pixel,
                      factory.getName() + " " + shape + " " + facing + " mixed ghost ink");
                }
              }
              assertTrue(count > 20, "ghost must be visible");
              assertTrue(count < image.getWidth() * image.getHeight() / 4,
                  "ghost must remain unfilled");
            }
          }
        }
      }
    }
  }
}
