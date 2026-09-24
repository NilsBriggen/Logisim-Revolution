/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit.appear;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cburch.draw.model.CanvasObject;
import com.cburch.draw.shapes.DrawAttr;
import com.cburch.draw.shapes.Rectangle;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.ReplacementMap;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.PrefMonitorBooleanConvert;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.ProbeAttributes;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultAppearancePaintTest {
  private static final Color DARK = new Color(0x191D23);
  private static final Color SCREEN_INK = new Color(0xE2E6ED);
  private static final Color PRINT_INK = new Color(AppPreferences.DEFAULT_COMPONENT_COLOR);
  private static final List<AttributeOption> STYLES =
      List.of(CircuitAttributes.APPEAR_CLASSIC, CircuitAttributes.APPEAR_FPGA);
  private static final List<Direction> FACINGS =
      List.of(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH);

  private static CircuitAppearance appearance(AttributeOption style) {
    final var circuit = new Circuit("counter_sub", null, null);
    circuit.getStaticAttributes().setValue(CircuitAttributes.APPEARANCE_ATTR, style);
    final var appearance = circuit.getAppearance();
    final var replacements = new ReplacementMap();
    for (final var output : new boolean[] {false, true}) {
      final var attrs = Pin.FACTORY.createAttributeSet();
      attrs.setValue(Pin.ATTR_TYPE, output ? Pin.OUTPUT : Pin.INPUT);
      attrs.setValue(StdAttr.FACING, output ? Direction.WEST : Direction.EAST);
      attrs.setValue(StdAttr.LABEL, output ? "count" : "clock");
      final var pin = Pin.FACTORY.createComponent(
          Location.create(output ? 100 : 50, 50, false), attrs);
      replacements.add(pin);
      // No test fixture should survive through the process-wide appearance-conversion listener.
      ((PrefMonitorBooleanConvert) AppPreferences.NEW_INPUT_OUTPUT_SHAPES)
          .removeConvertListener((ProbeAttributes) attrs);
    }
    appearance.getCircuitPins().transactionCompleted(replacements);
    return appearance;
  }

  private static BufferedImage render(
      CircuitAppearance appearance, Direction facing, boolean print, Color background) {
    final var image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB);
    final var g = image.createGraphics();
    try {
      g.setColor(background);
      g.fillRect(0, 0, image.getWidth(), image.getHeight());
      g.translate(250, 250);
      g.scale(2, 2);
      g.setFont(CanvasStyle.documentFont());
      final var transform = g.getTransform();
      final var painter = mock(InstancePainter.class);
      when(painter.isPrintView()).thenReturn(print);
      // Circuit.draw establishes this same context before drawing any subcircuit instance.
      final Runnable paint = () -> appearance.paintSubcircuit(painter, g, facing);
      if (print) AppPreferences.runWithPrintViewColors(paint);
      else paint.run();
      assertEquals(transform, g.getTransform());
    } finally {
      g.dispose();
    }
    return image;
  }

  private static int pixels(BufferedImage image, Color color) {
    var result = 0;
    for (var y = 0; y < image.getHeight(); y++) {
      for (var x = 0; x < image.getWidth(); x++) {
        if (image.getRGB(x, y) == color.getRGB()) result++;
      }
    }
    return result;
  }

  private static void assertUnchanged(
      CircuitAppearance appearance, List<CanvasObject> originals, List<CanvasObject> snapshots) {
    final var current = appearance.getObjectsFromBottom();
    assertEquals(originals.size(), current.size());
    for (var i = 0; i < current.size(); i++) {
      assertSame(originals.get(i), current.get(i), "painting replaced a cached shape");
      final var snapshot = snapshots.get(i);
      final var shape = current.get(i);
      if (snapshot instanceof AppearancePort expected && shape instanceof AppearancePort actual) {
        // Check geometry and pin identity independently of the port's matches implementation.
        assertEquals(expected.getLocation(), actual.getLocation(), "painting moved a port");
        assertEquals(expected.getBounds(), actual.getBounds(), "painting resized a port");
        assertSame(expected.getPin(), actual.getPin(), "painting reassigned a port");
      } else {
        assertTrue(snapshot.matches(shape), "painting changed shape attributes");
      }
    }
  }

  @Test
  void automaticClassicAndHolyCrossOutlinesUseScreenInkWithoutChangingGeometry() {
    for (final var ink : List.of(Color.BLACK, SCREEN_INK)) {
      try (final var colors = mockStatic(CanvasStyle.class, CALLS_REAL_METHODS)) {
        colors.when(CanvasStyle::componentColor).thenReturn(ink);
        for (final var style : STYLES) {
          final var appearance = appearance(style);
          final var bounds = appearance.getOffsetBounds();
          final var originals = List.copyOf(appearance.getObjectsFromBottom());
          final var snapshots = originals.stream().map(CanvasObject::clone).toList();
          for (final var shape : originals) {
            if (shape instanceof Rectangle) {
              assertEquals(ink, shape.getValue(DrawAttr.STROKE_COLOR));
            }
          }
          for (final var facing : FACINGS) {
            final var ports = appearance.getPortOffsets(facing);
            assertEquals(2, ports.size());
            final var image = render(appearance, facing, false,
                ink.equals(Color.BLACK) ? Color.WHITE : DARK);
            assertTrue(pixels(image, ink) > 100, "automatic box outline is not readable");
            assertEquals(bounds, appearance.getOffsetBounds());
            assertEquals(ports, appearance.getPortOffsets(facing));
            assertUnchanged(appearance, originals, snapshots);
          }
        }
      }
    }
  }

  @Test
  void printRegeneratesAutomaticInkWithoutReplacingCachedDarkShapesOrFiringEvents() {
    try (final var colors = mockStatic(CanvasStyle.class, CALLS_REAL_METHODS)) {
      colors.when(CanvasStyle::componentColor)
          .thenAnswer(invocation -> AppPreferences.inPrintView() ? PRINT_INK : SCREEN_INK);
      for (final var style : STYLES) {
        final var appearance = appearance(style);
        final var originals = List.copyOf(appearance.getObjectsFromBottom());
        final var snapshots = originals.stream().map(CanvasObject::clone).toList();
        final var listener = mock(CircuitAppearanceListener.class);
        appearance.addCircuitAppearanceListener(listener);
        final var bounds = appearance.getOffsetBounds();
        for (final var facing : FACINGS) {
          final var ports = appearance.getPortOffsets(facing);
          final var printed = render(appearance, facing, true, Color.WHITE);
          assertTrue(pixels(printed, PRINT_INK) > 100, "missing print outline/text");
          assertEquals(0, pixels(printed, SCREEN_INK), "cached dark-screen ink leaked to paper");
          assertUnchanged(appearance, originals, snapshots);
          assertEquals(bounds, appearance.getOffsetBounds());
          assertEquals(ports, appearance.getPortOffsets(facing));
          final var screen = render(appearance, facing, false, DARK);
          assertTrue(pixels(screen, SCREEN_INK) > 100, "print changed the next screen paint");
        }
        verifyNoInteractions(listener);
      }
    }
  }

  @Test
  void customBlackWhiteAndExplicitColorsRemainLiteralOnScreenAndPrint() {
    try (final var colors = mockStatic(CanvasStyle.class, CALLS_REAL_METHODS)) {
      colors.when(CanvasStyle::componentColor)
          .thenAnswer(invocation -> AppPreferences.inPrintView() ? PRINT_INK : SCREEN_INK);
      final var circuit = new Circuit("custom", null, null);
      circuit.getStaticAttributes().setValue(
          CircuitAttributes.APPEARANCE_ATTR, CircuitAttributes.APPEAR_CUSTOM);
      final var appearance = circuit.getAppearance();
      final var shapes = new ArrayList<CanvasObject>();
      final var fills = List.of(Color.BLACK, Color.WHITE, SCREEN_INK, Color.ORANGE);
      for (var i = 0; i < fills.size(); i++) {
        final var rectangle = new Rectangle(50 + i * 25, 50, 20, 20);
        rectangle.setValue(DrawAttr.PAINT_TYPE, DrawAttr.PAINT_FILL);
        rectangle.setValue(DrawAttr.FILL_COLOR, fills.get(i));
        shapes.add(rectangle);
      }
      shapes.add(new AppearanceAnchor(Location.create(50, 50, true)));
      appearance.setObjectsForce(shapes, false);
      final var originals = List.copyOf(appearance.getObjectsFromBottom());
      final var snapshots = originals.stream().map(CanvasObject::clone).toList();
      final var listener = mock(CircuitAppearanceListener.class);
      appearance.addCircuitAppearanceListener(listener);
      for (final var facing : FACINGS) {
        final var screen = render(appearance, facing, false, DARK);
        final var printed = render(appearance, facing, true, DARK);
        for (final var fill : fills) assertTrue(pixels(printed, fill) > 100);
        assertArrayEquals(screen.getRGB(0, 0, 500, 500, null, 0, 500),
            printed.getRGB(0, 0, 500, 500, null, 0, 500));
        assertUnchanged(appearance, originals, snapshots);
      }
      verifyNoInteractions(listener);
    }
  }
}
