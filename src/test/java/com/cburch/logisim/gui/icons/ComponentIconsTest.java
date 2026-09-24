/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */


package com.cburch.logisim.gui.icons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.std.Builtin;
import com.cburch.logisim.std.arith.Adder;
import com.cburch.logisim.std.arith.Subtractor;
import com.cburch.logisim.std.arith.floating.FpAdder;
import com.cburch.logisim.std.gates.GatesLibrary;
import com.cburch.logisim.std.memory.Ram;
import com.cburch.logisim.std.memory.Rom;
import com.cburch.logisim.std.plexers.Demultiplexer;
import com.cburch.logisim.std.plexers.Multiplexer;
import com.cburch.logisim.std.wiring.Clock;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.PowerOnReset;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.IconTestFactory;
import com.cburch.logisim.util.UiScale;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ComponentIconsTest {
  @Test
  void builtInCategoriesHaveVectorsExceptDeliberateSpecializedPainters() {
    final var retained = Set.of(
        "com.cburch.logisim.std.base.Image",
        "com.cburch.logisim.std.wiring.Pin",
        "com.cburch.logisim.std.wiring.PullResistor",
        "com.cburch.logisim.circuit.SplitterFactory");
    var count = 0;
    for (final var library : new Builtin().getLibraries()) {
      for (final var tool : library.getTools()) {
        if (!(tool instanceof AddTool add)) continue;
        final var factory = add.getFactory();
        assertNotNull(factory, tool.getName());
        final var type = factory.getClass();
        if (retained.contains(type.getName())
            || type.getPackageName().equals("com.cburch.logisim.std.gates")
                && !type.getSimpleName().equals("Pla")) continue;
        final var icon = ComponentIcons.forFactory(type);
        assertNotNull(icon, type.getName());
        assertTrue(hasInk(render(icon)), type.getName());
        count++;
      }
    }
    assertTrue(count > 150, "unexpectedly incomplete built-in coverage: " + count);
  }

  @Test
  void unknownFactoriesAndAttributeDependentGateAndPinShapesAreNotIntercepted() {
    assertNull(ComponentIcons.forFactory(IconTestFactory.class));
    final var gate = (AddTool) new GatesLibrary().getTools().get(0);
    assertNull(ComponentIcons.forFactory(gate.getFactory().getClass()));
    assertNull(ComponentIcons.forFactory(Pin.class));
  }

  @Test
  void relatedComponentsRemainVisuallyDistinct() {
    assertDifferent(ComponentIcons.forFactory(Adder.class), ComponentIcons.forFactory(FpAdder.class));
    assertDifferent(ComponentIcons.forFactory(Adder.class), ComponentIcons.forFactory(Subtractor.class));
    assertDifferent(ComponentIcons.forFactory(Ram.class), ComponentIcons.forFactory(Rom.class));
    assertDifferent(
        ComponentIcons.forFactory(Multiplexer.class), ComponentIcons.forFactory(Demultiplexer.class));
    assertDifferent(ComponentIcons.forFactory(Clock.class), ComponentIcons.forFactory(PowerOnReset.class));
  }

  @Test
  void retainedVectorUsesLiveSizeAndColorWithoutChangingCallerGraphics() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var oldScale = UiScale.factor();
      final var oldColor = AppPreferences.COMPONENT_ICON_COLOR.get();
      final var icon = ComponentIcons.forFactory(Adder.class);
      try {
        for (final var factor : new double[] {1, 1.6, 2}) {
          UiScale.setFactor(factor);
          assertEquals(AppPreferences.getIconSize(), icon.getIconWidth());
          assertEquals(icon.getIconWidth(), icon.getIconHeight());
          for (final var ink : new Color[] {new Color(28, 37, 49), new Color(231, 236, 243)}) {
            AppPreferences.COMPONENT_ICON_COLOR.set(ink.getRGB());
            final var image = render(icon);
            assertTrue(hasInk(image));
            var opaquePixels = 0;
            for (final var pixel : image.getRGB(
                0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth())) {
              // Antialiased edge pixels round RGB through premultiplied alpha; solid ink is exact.
              if ((pixel >>> 24) == 255) {
                assertEquals(ink.getRGB() & 0xffffff, pixel & 0xffffff);
                opaquePixels++;
              }
            }
            assertTrue(opaquePixels > 0, "no solid icon strokes at " + factor);
          }
          final var image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
          final var graphics = image.createGraphics();
          try {
            graphics.translate(7, 9);
            graphics.setColor(Color.ORANGE);
            final var transform = graphics.getTransform();
            final var stroke = graphics.getStroke();
            final var font = graphics.getFont();
            final var hints = graphics.getRenderingHints();
            icon.paintIcon(null, graphics, 4, 5);
            assertEquals(transform, graphics.getTransform());
            assertEquals(stroke, graphics.getStroke());
            assertEquals(font, graphics.getFont());
            assertEquals(hints, graphics.getRenderingHints());
            assertEquals(Color.ORANGE, graphics.getColor());
          } finally {
            graphics.dispose();
          }
        }
      } finally {
        UiScale.setFactor(oldScale);
        AppPreferences.COMPONENT_ICON_COLOR.set(oldColor);
      }
    });
  }

  private static void assertDifferent(Icon first, Icon second) {
    final var a = render(first);
    final var b = render(second);
    final var size = a.getWidth();
    assertFalse(Arrays.equals(
        a.getRGB(0, 0, size, size, null, 0, size),
        b.getRGB(0, 0, size, size, null, 0, size)));
  }

  private static BufferedImage render(Icon icon) {
    final var size = icon.getIconWidth();
    final var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    final var graphics = image.createGraphics();
    try {
      icon.paintIcon(null, graphics, 0, 0);
    } finally {
      graphics.dispose();
    }
    return image;
  }

  private static boolean hasInk(BufferedImage image) {
    for (final var pixel : image.getRGB(
        0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth())) {
      if ((pixel >>> 24) != 0) return true;
    }
    return false;
  }
}
