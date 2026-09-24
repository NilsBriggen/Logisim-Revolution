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

import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.UiScale;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

/** Exercises the actual component painters on disposable images, without circuits or preferences. */
class WiringPainterTest {
  private static final List<Direction> FACINGS =
      List.of(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH);
  private static final Color DARK_SURFACE = new Color(0x242830);
  private static final Color LIGHT_INK = new Color(0xE2E6ED);

  private record Fixture(InstanceFactory factory, AttributeSet attributes) {}

  private record Render(BufferedImage image, Rectangle interior) {}

  private static Fixture constant(int bits, long value) {
    final var attributes = Constant.FACTORY.createAttributeSet();
    attributes.setValue(StdAttr.WIDTH, BitWidth.create(bits));
    attributes.setValue(Constant.ATTR_VALUE, value);
    return new Fixture(Constant.FACTORY, attributes);
  }

  private static List<Fixture> resets() {
    final var result = new ArrayList<Fixture>();
    for (final var size : List.of("3", "1", "2")) {
      for (final var transition : List.of("1", "2")) {
        final var attributes = PowerOnReset.FACTORY.createAttributeSet();
        setParsed(attributes, attributes.getAttribute("porsize"), size);
        setParsed(attributes, attributes.getAttribute("porTransition"), transition);
        result.add(new Fixture(PowerOnReset.FACTORY, attributes));
      }
    }
    return result;
  }

  private static <T> void setParsed(AttributeSet attributes, Attribute<T> attribute, String text) {
    attributes.setValue(attribute, attribute.parse(text));
  }

  private static Map<String, String> storedValues(AttributeSet attributes) {
    final var values = new LinkedHashMap<String, String>();
    for (final var attribute : attributes.getAttributes()) {
      values.put(attribute.getName(), storedValue(attributes, attribute));
    }
    return values;
  }

  private static <T> String storedValue(AttributeSet attributes, Attribute<T> attribute) {
    return attribute.toStandardString(attributes.getValue(attribute));
  }

  private static Render render(Fixture fixture, int zoom, boolean print, Color canvas) {
    final var location = Location.create(100, 100, false);
    final var component = (InstanceComponent) fixture.factory()
        .createComponent(location, fixture.attributes());
    final var bounds = component.getBounds();
    final var padding = 8;
    final var image = new BufferedImage((bounds.getWidth() + padding * 2) * zoom,
        (bounds.getHeight() + padding * 2) * zoom, BufferedImage.TYPE_INT_ARGB);
    final var graphics = image.createGraphics();
    try {
      graphics.setColor(canvas);
      graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
      graphics.scale(zoom, zoom);
      graphics.translate(padding - bounds.getX(), padding - bounds.getY());
      graphics.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
      final var context = new ComponentDrawContext(null, null, null, graphics, graphics, print);
      context.setShowState(false);
      fixture.factory().paintInstance(new InstancePainter(context, component));
      assertEquals(location, component.getEnd(0).getLocation(),
          "painting must not move the output pin");
    } finally {
      graphics.dispose();
    }
    return new Render(image, new Rectangle((padding + 2) * zoom, (padding + 2) * zoom,
        (bounds.getWidth() - 4) * zoom, (bounds.getHeight() - 4) * zoom));
  }

  private static int pixels(Render render, Color color) {
    var count = 0;
    final var area = render.interior();
    for (var y = area.y; y < area.y + area.height; y++) {
      for (var x = area.x; x < area.x + area.width; x++) {
        if (render.image().getRGB(x, y) == color.getRGB()) count++;
      }
    }
    return count;
  }

  @Test
  void constantAndResetUsePairedSurfaceAndInkInBothThemesAndAllFacings() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var oldBackground = UIManager.get("TextField.background");
      final var oldForeground = UIManager.get("TextField.foreground");
      try {
        final var fixtures = new ArrayList<>(resets());
        fixtures.add(constant(8, 0xA5));
        for (final var dark : new boolean[] {false, true}) {
          final var surface = dark ? DARK_SURFACE : Color.WHITE;
          final var ink = dark ? LIGHT_INK : Color.BLACK;
          UIManager.put("TextField.background", surface);
          UIManager.put("TextField.foreground", ink);
          for (final var fixture : fixtures) {
            for (final var facing : FACINGS) {
              fixture.attributes().setValue(StdAttr.FACING, facing);
              final var saved = storedValues(fixture.attributes());
              for (final var zoom : new int[] {1, 2}) {
                final var rendered = render(fixture, zoom, false, new Color(0x17191D));
                final var identity = fixture.factory().getName() + " " + saved + " zoom " + zoom;
                assertTrue(pixels(rendered, surface) > 20, identity + ": no paired surface");
                assertTrue(pixels(rendered, ink) > 5, identity + ": no readable interior text");
                assertEquals(saved, storedValues(fixture.attributes()));
              }
            }
          }
        }
      } finally {
        UIManager.put("TextField.background", oldBackground);
        UIManager.put("TextField.foreground", oldForeground);
      }
    });
  }

  @Test
  void printViewUsesBlackTextOnWhiteEvenWithDarkScreenDefaults() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var oldBackground = UIManager.get("TextField.background");
      final var oldForeground = UIManager.get("TextField.foreground");
      try {
        UIManager.put("TextField.background", DARK_SURFACE);
        UIManager.put("TextField.foreground", LIGHT_INK);
        AppPreferences.runWithPrintViewColors(() -> {
          final var fixtures = new ArrayList<>(resets());
          fixtures.add(constant(8, 0x5A));
          fixtures.add(constant(1, 0));
          fixtures.add(constant(1, 1));
          for (final var fixture : fixtures) {
            for (final var facing : FACINGS) {
              fixture.attributes().setValue(StdAttr.FACING, facing);
              final var identity = fixture.factory().getName() + " "
                  + storedValues(fixture.attributes());
              final var rendered = render(fixture, 2, true, Color.WHITE);
              assertTrue(pixels(rendered, Color.BLACK) > 5, identity + ": missing print ink");
              assertTrue(pixels(rendered, Color.WHITE) > 20, identity + ": missing print surface");
              assertEquals(0, pixels(rendered, DARK_SURFACE),
                  identity + ": screen fill leaked into print view");
              assertEquals(0, pixels(rendered, LIGHT_INK),
                  identity + ": screen ink leaked into print view");
            }
          }
        });
      } finally {
        UIManager.put("TextField.background", oldBackground);
        UIManager.put("TextField.foreground", oldForeground);
      }
    });
  }

  @Test
  void oneBitConstantRetainsItsSignalValueColorAndStoredValue() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      for (final var bit : new long[] {0, 1}) {
        final var fixture = constant(1, bit);
        final var saved = storedValues(fixture.attributes());
        final var rendered = render(fixture, 2, false, Color.WHITE);
        assertTrue(pixels(rendered, bit == 0 ? Value.FALSE.getColor() : Value.TRUE.getColor()) > 5);
        assertEquals(saved, storedValues(fixture.attributes()));
        assertEquals(bit, fixture.attributes().getValue(Constant.ATTR_VALUE).longValue());
      }
    });
  }

  @Test
  void interfaceZoomDoesNotRescaleCircuitSymbolsOrDocumentFonts() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var original = UiScale.factor();
      try {
        final var fixtures = new ArrayList<>(resets());
        fixtures.add(constant(8, 0xA5));
        fixtures.add(new Fixture(Clock.FACTORY, Clock.FACTORY.createAttributeSet()));
        for (final var fixture : fixtures) {
          UiScale.setFactor(1);
          final var baseline = render(fixture, 2, false, Color.WHITE).image();
          final var width = baseline.getWidth();
          final var height = baseline.getHeight();
          final var pixels = baseline.getRGB(0, 0, width, height, null, 0, width);
          for (final var factor : new double[] {1.6, 2}) {
            UiScale.setFactor(factor);
            final var scaled = render(fixture, 2, false, Color.WHITE).image();
            assertEquals(width, scaled.getWidth());
            assertEquals(height, scaled.getHeight());
            assertArrayEquals(pixels, scaled.getRGB(0, 0, width, height, null, 0, width),
                fixture.factory().getName() + " mixed interface zoom into circuit coordinates");
          }
        }
      } finally {
        UiScale.setFactor(original);
      }
    });
  }

  @Test
  void allGhostsUseSuppliedInkWithoutFillsOrGraphicsStateLeakage() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var fixtures = new ArrayList<>(resets());
      fixtures.add(constant(8, 0x5A));
      fixtures.add(constant(1, 1));
      for (final var appearance :
          List.of(StdAttr.APPEAR_CLASSIC, ProbeAttributes.APPEAR_EVOLUTION_NEW)) {
        final var attributes = Clock.FACTORY.createAttributeSet();
        attributes.setValue(ProbeAttributes.PROBEAPPEARANCE, appearance);
        fixtures.add(new Fixture(Clock.FACTORY, attributes));
      }
      for (final var fixture : fixtures) {
        for (final var facing : FACINGS) {
          fixture.attributes().setValue(StdAttr.FACING, facing);
          for (final var zoom : new int[] {1, 2}) {
            assertGhost(fixture, zoom);
          }
        }
      }
    });
  }

  private static void assertGhost(Fixture fixture, int zoom) {
    final var bounds = fixture.factory().getOffsetBounds(fixture.attributes());
    final var image = new BufferedImage((bounds.getWidth() + 16) * zoom,
        (bounds.getHeight() + 16) * zoom, BufferedImage.TYPE_INT_ARGB);
    final var graphics = image.createGraphics();
    final var ink = new Color(40, 160, 224);
    try {
      graphics.scale(zoom, zoom);
      graphics.translate(8 - bounds.getX(), 8 - bounds.getY());
      graphics.setColor(ink);
      graphics.setFont(new Font(Font.SERIF, Font.ITALIC, 17));
      graphics.setStroke(new BasicStroke(3));
      graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
      final var transform = graphics.getTransform();
      final var font = graphics.getFont();
      final var stroke = graphics.getStroke();
      final var composite = graphics.getComposite();
      final var saved = storedValues(fixture.attributes());
      final var context = new ComponentDrawContext(null, null, null, graphics, graphics);
      fixture.factory().drawGhost(context, ink, 0, 0, fixture.attributes());
      assertEquals(ink, graphics.getColor());
      assertEquals(font, graphics.getFont());
      assertEquals(stroke, graphics.getStroke());
      assertEquals(composite, graphics.getComposite());
      assertEquals(transform, graphics.getTransform());
      assertEquals(saved, storedValues(fixture.attributes()));
      var drawn = 0;
      var transparent = 0;
      for (var y = 0; y < image.getHeight(); y++) {
        for (var x = 0; x < image.getWidth(); x++) {
          final var pixel = new Color(image.getRGB(x, y), true);
          if (pixel.getAlpha() == 0) {
            transparent++;
            continue;
          }
          drawn++;
          assertTrue(Math.abs(pixel.getRed() - ink.getRed()) <= 2
              && Math.abs(pixel.getGreen() - ink.getGreen()) <= 2
              && Math.abs(pixel.getBlue() - ink.getBlue()) <= 2,
              fixture.factory().getName() + " replaced the supplied ghost ink");
        }
      }
      assertTrue(drawn > 5, fixture.factory().getName() + " ghost is empty");
      assertTrue(transparent > drawn, "ghost should not paint an opaque body");
    } finally {
      graphics.dispose();
    }
  }
}
