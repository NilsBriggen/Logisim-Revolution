/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.soc.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.soc.bus.SocBusAttributes;
import com.cburch.logisim.soc.dma.SocDma;
import com.cburch.logisim.soc.gui.CpuStyle;
import com.cburch.logisim.std.io.RealTimeClock;
import com.cburch.logisim.util.ColorUtil;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

/** Actual painter rasters without windows, circuit files, propagation or preference writes. */
class AutomaticPainterContrastTest {
  private static final Color DARK = new Color(0x242830);
  private static final Color LIGHT = new Color(0xE2E6ED);

  private static void withTheme(boolean dark, Runnable action) {
    final var values = new LinkedHashMap<String, Object>();
    final var colors = Map.of(
        "TextField.background", dark ? DARK : Color.WHITE,
        "TextField.foreground", dark ? LIGHT : Color.BLACK,
        "Logisim.accent", dark ? new Color(0x355A90) : new Color(0x2456A0),
        "Logisim.accentText", Color.WHITE,
        "Logisim.warning", dark ? new Color(0xF0B429) : new Color(0xB7791F));
    colors.forEach((key, value) -> {
      values.put(key, UIManager.get(key));
      UIManager.put(key, value);
    });
    try {
      action.run();
    } finally {
      values.forEach(UIManager::put);
    }
  }

  private static void printMode(boolean print, Runnable action) {
    if (print) AppPreferences.runWithPrintViewColors(action);
    else action.run();
  }

  private static int pixels(BufferedImage image, Rectangle area, Color color) {
    var count = 0;
    for (var y = area.y; y < area.y + area.height; y++) {
      for (var x = area.x; x < area.x + area.width; x++) {
        if (image.getRGB(x, y) == color.getRGB()) count++;
      }
    }
    return count;
  }

  private static <T> void parse(AttributeSet attrs, Attribute<T> attr, String value) {
    attrs.setValue(attr, attr.parse(value));
  }

  private static <T> String stored(AttributeSet attrs, Attribute<T> attr) {
    return attr.toStandardString(attrs.getValue(attr));
  }

  private static Map<String, String> stored(AttributeSet attrs) {
    final var result = new LinkedHashMap<String, String>();
    for (final var attr : attrs.getAttributes()) {
      if (!attr.isHidden()) result.put(attr.getName(), stored(attrs, attr));
    }
    return result;
  }

  private static BufferedImage render(
      InstanceFactory factory, AttributeSet attrs, boolean print, int zoom) {
    final var location = Location.create(100, 80, false);
    final var component = (InstanceComponent) factory.createComponent(location, attrs);
    final var bounds = component.getBounds();
    final var saved = stored(attrs);
    final var image = new BufferedImage(
        (bounds.getWidth() + 16) * zoom, (bounds.getHeight() + 16) * zoom,
        BufferedImage.TYPE_INT_ARGB);
    final var g = image.createGraphics();
    try {
      g.setColor(print ? Color.WHITE : new Color(0x15191F));
      g.fillRect(0, 0, image.getWidth(), image.getHeight());
      g.scale(zoom, zoom);
      g.translate(8 - bounds.getX(), 8 - bounds.getY());
      g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
      final var context = new ComponentDrawContext(
          null, null, mock(CircuitState.class), g, g, print);
      context.setShowState(false);
      factory.paintInstance(new InstancePainter(context, component));
      assertEquals(bounds, component.getBounds());
      assertEquals(saved, stored(attrs), "painting changed stored attributes");
    } finally {
      g.dispose();
    }
    return image;
  }

  @Test
  void realTimeClockPairsCaptionAndDialWithSurfaceAtAllSizesAndFacings() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      for (final var dark : new boolean[] {false, true}) {
        withTheme(dark, () -> {
          for (final var print : new boolean[] {false, true}) {
            printMode(print, () -> {
              for (final var size : List.of("1", "2")) {
                for (final var facing :
                    List.of(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH)) {
                  final var attrs = RealTimeClock.FACTORY.createAttributeSet();
                  parse(attrs, attrs.getAttribute("porsize"), size);
                  attrs.setValue(StdAttr.FACING, facing);
                  for (final var zoom : new int[] {1, 2}) {
                    final var image = render(RealTimeClock.FACTORY, attrs, print, zoom);
                    final var area = new Rectangle(10 * zoom, 10 * zoom,
                        image.getWidth() - 20 * zoom, image.getHeight() - 20 * zoom);
                    assertTrue(pixels(image, area, CanvasStyle.dataBackground()) > 20);
                    assertTrue(pixels(image, area, CanvasStyle.dataForeground()) > 5,
                        "missing RTC caption/dial ink: " + stored(attrs) + " print=" + print);
                    if (print) assertEquals(0, pixels(image, area, DARK));
                  }
                }
              }
            });
          }
        });
      }
    });
  }

  @Test
  void hiddenBusTraceUsesPairedSurfaceAndTextIncludingPrint() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      // Bypass only JDialog construction: paint uses no dialog fields or native peers.
      final var painter = mock(SocBusStateInfo.class, CALLS_REAL_METHODS);
      final var instance = mock(Instance.class);
      when(instance.getAttributeValue(SocBusAttributes.NrOfTracesAttr))
          .thenReturn(BitWidth.create(2));
      for (final var dark : new boolean[] {false, true}) {
        withTheme(dark, () -> {
          for (final var print : new boolean[] {false, true}) {
            printMode(print, () -> {
              final var image = new BufferedImage(720, 150, BufferedImage.TYPE_INT_ARGB);
              final var g = image.createGraphics();
              try {
                g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
                painter.paint(30, 20, g, instance, false, null);
              } finally {
                g.dispose();
              }
              final var area = new Rectangle(37, 47, 626, 56);
              assertTrue(pixels(image, area, CanvasStyle.dataBackground()) > 1000);
              assertTrue(pixels(image, area, CanvasStyle.dataForeground()) > 20);
              assertEquals(0, pixels(image, area, Color.YELLOW));
              assertEquals(0, image.getRGB(0, 0), "absolute placement changed");
            });
          }
        });
      }
    });
  }

  @Test
  void connectedAndDisconnectedBusStripsRetainStatusFillsWithPairedInk() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var manager = mock(SocSimulationManager.class);
      when(manager.getSocBusDisplayString("bus")).thenReturn("Main bus");
      for (final var dark : new boolean[] {false, true}) {
        withTheme(dark, () -> {
          for (final var print : new boolean[] {false, true}) {
            printMode(print, () -> {
              for (final var connected : new boolean[] {false, true}) {
                final var info = new SocBusInfo("bus");
                if (connected) info.setSocSimulationManager(manager, null);
                final var image = new BufferedImage(380, 80, BufferedImage.TYPE_INT_ARGB);
                final var g = image.createGraphics();
                try {
                  g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
                  info.paint(g, Bounds.create(30, 20, 320, 30));
                } finally {
                  g.dispose();
                }
                final var fill = connected ? CpuStyle.header() : CpuStyle.programCounter();
                final var ink = connected
                    ? CpuStyle.headerText() : ColorUtil.getComplementaryBlackWhite(fill);
                final var area = new Rectangle(32, 22, 316, 26);
                assertTrue(pixels(image, area, fill) > 1000, "status fill changed");
                assertTrue(pixels(image, area, ink) > 20, "status text lacks paired ink");
                assertEquals("bus", info.getBusId());
                assertEquals(0, image.getRGB(0, 0));
              }
            });
          }
        });
      }
    });
  }

  @Test
  void dmaConnectionCaptionsUseComponentInkWithoutChangingPrintVisibility() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      for (final var ink : List.of(new Color(0x222831), LIGHT)) {
        // Substitute the automatic ink provider, never a persisted preference.
        try (final var style = mockStatic(CanvasStyle.class, CALLS_REAL_METHODS)) {
          style.when(CanvasStyle::componentColor).thenReturn(ink);
          final var factory = new SocDma();
          final var attrs = factory.createAttributeSet();
          final var image = render(factory, attrs, false, 1);
          for (final var x : new int[] {13, 118, 223}) {
            final var caption = new Rectangle(x, 90, 100, 12);
            assertTrue(pixels(image, caption, ink) > 5, "missing CTRL/SRC/DST component ink");
            assertEquals(0, pixels(image, caption, Color.BLACK), "literal black caption remains");
          }
          printMode(true, () -> {
            final var printed = render(factory, attrs, true, 1);
            final var captions = new Rectangle(13, 90, 310, 12);
            assertEquals(captions.width * captions.height,
                pixels(printed, captions, Color.WHITE), "print-only omission changed");
          });
        }
      }
    });
  }
}

