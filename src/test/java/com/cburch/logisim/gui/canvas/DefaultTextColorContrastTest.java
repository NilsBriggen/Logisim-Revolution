/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.std.Builtin;
import com.cburch.logisim.std.base.Text;
import com.cburch.logisim.std.io.IoLibrary;
import com.cburch.logisim.std.io.Tty;
import com.cburch.logisim.tools.AddTool;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Text a user has not coloured must be readable on every canvas it can land on.
 *
 * <p>Default label and text colours used to be fixed light-theme values, so on the dark canvas a
 * Text was black, a TTY printed black and every I/O label was {@code #0000FF}. The defaults are now
 * placeholders resolved at paint time; this sweeps every built-in component's default text colours
 * through that resolution and holds the result to the WCAG 4.5:1 for text on the light canvas, the
 * dark canvas and paper.
 */
class DefaultTextColorContrastTest {

  private static final double TEXT_CONTRAST = 4.5;

  /**
   * Colour attributes that fill a shape (a LED, a button face, a background, a trace) rather than
   * colour text. Their contrast is a matter of what they mean, not of legibility.
   */
  private static final Set<String> FILL_COLOR_ATTRIBUTES =
      Set.of("color", "offcolor", "bg", "reversecolor");

  private record Canvas(String name, Color background, Color ink, boolean dark, boolean print) {}

  private static final Canvas[] CANVASES = {
    new Canvas(
        "light canvas",
        new Color(AppPreferences.DEFAULT_CANVAS_BG_COLOR),
        new Color(AppPreferences.DEFAULT_COMPONENT_COLOR),
        false,
        false),
    new Canvas(
        "dark canvas",
        new Color(AppPreferences.DARK_CANVAS_BG_COLOR),
        new Color(AppPreferences.DARK_COMPONENT_COLOR),
        true,
        false),
    // Printing from a dark window still draws on white, in the print palette.
    new Canvas(
        "paper",
        Color.WHITE,
        new Color(AppPreferences.DEFAULT_COMPONENT_COLOR),
        true,
        true),
  };

  static double contrast(Color a, Color b) {
    final var la = luminance(a);
    final var lb = luminance(b);
    return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
  }

  private static double luminance(Color color) {
    return 0.2126 * channel(color.getRed())
        + 0.7152 * channel(color.getGreen())
        + 0.0722 * channel(color.getBlue());
  }

  private static double channel(int value) {
    final var c = value / 255.0;
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  }

  /** What a text colour attribute of {@code factory} is painted as on {@code canvas}. */
  private static Color painted(
      ComponentFactory factory, Attribute<?> attr, Color value, Canvas canvas) {
    if (attr == StdAttr.LABEL_COLOR) {
      return CanvasStyle.labelColor(value, canvas.dark(), canvas.print());
    }
    if (attr == Text.ATTR_COLOR) return CanvasStyle.textColor(value, canvas.ink());
    if (attr == IoLibrary.ATTR_COLOR && factory instanceof Tty) {
      final var background =
          factory.createAttributeSet().getAttributes().stream()
              .filter(a -> "bg".equals(a.getName()))
              .map(a -> (Color) factory.createAttributeSet().getValue(a))
              .findFirst()
              .orElse(null);
      return Tty.textColor(value, background, canvas.ink());
    }
    return null;
  }

  @Test
  void everyBuiltinDefaultTextColourIsReadableOnEveryCanvas() {
    final var failures = new ArrayList<String>();
    var checked = 0;
    for (final var library : new Builtin().getLibraries()) {
      for (final var tool : library.getTools()) {
        if (!(tool instanceof AddTool add)) continue;
        final var factory = add.getFactory();
        if (factory == null) continue;
        final var attrs = factory.createAttributeSet();
        for (final var attr : attrs.getAttributes()) {
          if (!(attrs.getValue(attr) instanceof Color value)) continue;
          final var onLight = painted(factory, attr, value, CANVASES[0]);
          if (onLight == null) {
            assertTrue(
                FILL_COLOR_ATTRIBUTES.contains(attr.getName()),
                factory.getName() + "." + attr.getName()
                    + " is a colour attribute this test does not know; classify it as text or fill");
            continue;
          }
          checked++;
          for (final var canvas : CANVASES) {
            final var color = painted(factory, attr, value, canvas);
            final var ratio = contrast(color, canvas.background());
            if (ratio < TEXT_CONTRAST) {
              failures.add(
                  String.format(
                      "%s.%s default %06X is painted %06X on the %s: %.2f:1",
                      factory.getName(),
                      attr.getName(),
                      value.getRGB() & 0xFFFFFF,
                      color.getRGB() & 0xFFFFFF,
                      canvas.name(),
                      ratio));
            }
          }
        }
      }
    }
    assertTrue(checked >= 8, "the sweep found only " + checked + " text colours");
    assertTrue(failures.isEmpty(), String.join("\n", failures));
  }

  /** An explicit colour saved in a file is painted exactly as saved, on any canvas. */
  @Test
  void anExplicitColourIsPaintedAsSaved() {
    final var chosen = new Color(0x123456);
    for (final var canvas : CANVASES) {
      assertSame(chosen, CanvasStyle.labelColor(chosen, canvas.dark(), canvas.print()));
      assertSame(chosen, CanvasStyle.textColor(chosen, canvas.ink()));
      assertSame(chosen, Tty.textColor(chosen, new Color(0, 0, 0, 0), canvas.ink()));
    }
  }

  @Test
  void printingKeepsThePrintPalette() {
    assertEquals(StdAttr.DEFAULT_LABEL_COLOR, CanvasStyle.labelColor(null, true, true));
    assertEquals(
        StdAttr.DEFAULT_LABEL_COLOR,
        CanvasStyle.labelColor(StdAttr.DEFAULT_LABEL_COLOR, true, true));
    final var ink = new int[1];
    AppPreferences.runWithPrintViewColors(
        () -> ink[0] = CanvasStyle.textColor(CanvasStyle.SHIPPED_TEXT_COLOR).getRGB());
    assertEquals(AppPreferences.DEFAULT_COMPONENT_COLOR & 0xFFFFFF, ink[0] & 0xFFFFFF);
  }

  /** A TTY given an opaque background keeps text readable on that background. */
  @Test
  void ttyTextOnAnOpaqueBackgroundUsesTheBetterInk() {
    final var ink = new Color(AppPreferences.DARK_COMPONENT_COLOR);
    assertEquals(Color.BLACK, Tty.textColor(Color.BLACK, Color.WHITE, ink));
    assertEquals(Color.WHITE, Tty.textColor(Color.BLACK, new Color(0x202020), ink));
    assertEquals(ink, Tty.textColor(Color.BLACK, new Color(255, 255, 255, 0), ink));
  }

  /** The shipped defaults themselves are unchanged, so files keep not recording them. */
  @Test
  void theShippedDefaultsAreStillTheFileDefaults() {
    assertEquals(
        CanvasStyle.SHIPPED_TEXT_COLOR,
        Text.FACTORY.createAttributeSet().getValue(Text.ATTR_COLOR));
    assertFalse(
        CanvasStyle.labelColor(StdAttr.DEFAULT_LABEL_COLOR, true, false)
            .equals(StdAttr.DEFAULT_LABEL_COLOR));
  }
}
