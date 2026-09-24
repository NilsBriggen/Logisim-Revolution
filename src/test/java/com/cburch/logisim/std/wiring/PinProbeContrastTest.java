/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.PrefMonitorBooleanConvert;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class PinProbeContrastTest {
  private static final Color CAPTION = new Color(0x9149A3);
  private static final int ZOOM = 2;

  private record Render(BufferedImage image, Rectangle oval) {}

  private static int pixels(BufferedImage image, Rectangle area, Color color) {
    var count = 0;
    for (var y = area.y; y < area.y + area.height; y++) {
      for (var x = area.x; x < area.x + area.width; x++) {
        if (image.getRGB(x, y) == color.getRGB()) count++;
      }
    }
    return count;
  }

  private static Rectangle scaled(Rectangle area) {
    return new Rectangle(area.x * ZOOM, area.y * ZOOM,
        area.width * ZOOM, area.height * ZOOM);
  }

  private static Render renderPin(AttributeOption appearance, Value value, boolean print) {
    final var attrs = (PinAttributes) Pin.FACTORY.createAttributeSet();
    attrs.setValue(ProbeAttributes.PROBEAPPEARANCE, appearance);
    attrs.setValue(RadixOption.ATTRIBUTE, RadixOption.RADIX_2);
    attrs.setValue(StdAttr.FACING, Direction.EAST);
    final var labelFont = attrs.getValue(StdAttr.LABEL_FONT);
    final var component = (InstanceComponent) Pin.FACTORY.createComponent(
        Location.create(100, 80, false), attrs);
    final var bounds = component.getBounds();
    final var image = new BufferedImage((bounds.getWidth() + 16) * ZOOM,
        (bounds.getHeight() + 16) * ZOOM, BufferedImage.TYPE_INT_ARGB);
    final var g = image.createGraphics();
    try {
      g.scale(ZOOM, ZOOM);
      g.translate(8 - bounds.getX(), 8 - bounds.getY());
      final var state = mock(CircuitState.class);
      when(state.getValue(any())).thenReturn(value);
      final var context = new ComponentDrawContext(null, null, state, g, g, print);
      final var painter = new InstancePainter(context, component) {
        private InstanceData data;

        @Override
        public InstanceData getData() {
          return data;
        }

        @Override
        public void setData(InstanceData value) {
          data = value;
        }
      };
      Pin.FACTORY.driveInputPin(painter, value);
      Pin.FACTORY.paintInstance(painter);
      assertEquals(value, Pin.FACTORY.getValue(painter), "paint changed the driven value");
      assertEquals(bounds, component.getBounds());
      assertEquals(appearance, attrs.getValue(ProbeAttributes.PROBEAPPEARANCE));
      assertEquals(labelFont, attrs.getValue(StdAttr.LABEL_FONT));
    } finally {
      g.dispose();
      ((PrefMonitorBooleanConvert) AppPreferences.NEW_INPUT_OUTPUT_SHAPES)
          .removeConvertListener(attrs);
    }
    final var oval = appearance == StdAttr.APPEAR_CLASSIC
        ? new Rectangle(13, 12, 11, 13)
        : new Rectangle(8 + bounds.getWidth() - 29,
            8 + 1 + 2 * ((bounds.getHeight() - 1) / 2) - 17, 9, 14);
    return new Render(image, scaled(oval));
  }

  @Test
  void classicAndEvolutionInputGlyphsContrastWithUnchangedSignalFill() {
    // The medium gray catches weighted-RGB threshold shortcuts: WCAG prefers black here.
    for (final var fill : List.of(Color.GREEN, new Color(0x004000), new Color(0x777777))) {
      final var expectedInk = fill.equals(new Color(0x004000)) ? Color.WHITE : Color.BLACK;
      try (final var colors = mockStatic(Value.class, CALLS_REAL_METHODS)) {
        colors.when(Value::trueColor).thenReturn(fill);
        colors.when(Value::falseColor).thenReturn(fill);
        for (final var value : List.of(Value.TRUE, Value.FALSE)) {
          for (final var appearance :
              List.of(StdAttr.APPEAR_CLASSIC, ProbeAttributes.APPEAR_EVOLUTION_NEW)) {
            final var result = renderPin(appearance, value, false);
            assertTrue(pixels(result.image(), result.oval(), fill) > 30,
                "the original signal fill must remain");
            assertTrue(pixels(result.image(), result.oval(), expectedInk) > 5,
                appearance + " glyph ink does not contrast with " + fill);
            assertEquals(fill, value.getColor(), "paint changed a signal color");
          }
        }
      }
    }
  }

  @Test
  void pinAndProbeRadixCaptionsUseComponentInkInsteadOfBlue() {
    try (final var style = mockStatic(CanvasStyle.class, CALLS_REAL_METHODS)) {
      style.when(CanvasStyle::componentColor).thenReturn(CAPTION);
      final var pin = renderPin(ProbeAttributes.APPEAR_EVOLUTION_NEW, Value.TRUE, false);
      final var pinArea = new Rectangle(0, 0, pin.image().getWidth(), pin.image().getHeight());
      assertEquals(0, pixels(pin.image(), pinArea, Color.BLUE), "pin radix is still blue");
      assertTrue(pixels(pin.image(), pinArea, CAPTION) > 5);

      final var attrs = Probe.FACTORY.createAttributeSet();
      attrs.setValue(ProbeAttributes.PROBEAPPEARANCE, ProbeAttributes.APPEAR_EVOLUTION_NEW);
      attrs.setValue(RadixOption.ATTRIBUTE, RadixOption.RADIX_16);
      final var component = new InstanceComponent(
          Probe.FACTORY, Location.create(70, 50, false), attrs);
      final var bounds = component.getBounds();
      final var image = new BufferedImage(160, 100, BufferedImage.TYPE_INT_ARGB);
      final var g = image.createGraphics();
      try {
        g.scale(ZOOM, ZOOM);
        g.translate(8 - bounds.getX(), 8 - bounds.getY());
        final var context = new ComponentDrawContext(null, null, null, g, g);
        Probe.paintValue(new InstancePainter(context, component), Value.TRUE, false);
      } finally {
        g.dispose();
      }
      final var area = new Rectangle(0, 0, image.getWidth(), image.getHeight());
      assertEquals(0, pixels(image, area, Color.BLUE), "probe radix is still blue");
      assertTrue(pixels(image, area, CAPTION) > 5);
    }
  }

  @Test
  void printKeepsSuppressingInputStateOvals() {
    final var signalFill = new Color(0x36E952);
    try (final var colors = mockStatic(Value.class, CALLS_REAL_METHODS)) {
      colors.when(Value::trueColor).thenReturn(signalFill);
      colors.when(Value::falseColor).thenReturn(signalFill);
      AppPreferences.runWithPrintViewColors(() -> {
        for (final var appearance :
            List.of(StdAttr.APPEAR_CLASSIC, ProbeAttributes.APPEAR_EVOLUTION_NEW)) {
          final var result = renderPin(appearance, Value.TRUE, true);
          final var area = new Rectangle(
              0, 0, result.image().getWidth(), result.image().getHeight());
          assertEquals(0, pixels(result.image(), area, signalFill));
          assertEquals(0, pixels(result.image(), area, Color.BLUE));
          assertTrue(pixels(result.image(), area, CanvasStyle.componentColor()) > 5);
        }
      });
    }
  }
}

