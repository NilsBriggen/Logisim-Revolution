/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.prefs.AppPreferences;
import java.awt.Color;
import org.junit.jupiter.api.Test;

/**
 * What a circuit is drawn in when it is going to paper or to an exported image.
 *
 * <p>The theme's colours are chosen against the window's background. On a white sheet a dark
 * theme's near-white stroke disappears altogether, so drawing for print uses the shipped light
 * palette whatever the window is showing.
 */
class PrintViewColorsTest {

  private static Color underPrintView(java.util.function.Supplier<Color> color) {
    final var result = new Color[1];
    AppPreferences.runWithPrintViewColors(() -> result[0] = color.get());
    return result[0];
  }

  @Test
  void signalColoursUsePrintPaletteWhenPrinting() {
    assertEquals(new Color(AppPreferences.PRINT_TRUE_COLOR), underPrintView(Value::trueColor));
    assertEquals(new Color(AppPreferences.PRINT_FALSE_COLOR), underPrintView(Value::falseColor));
    assertEquals(
        new Color(AppPreferences.PRINT_UNKNOWN_COLOR), underPrintView(Value::unknownColor));
    assertEquals(new Color(AppPreferences.PRINT_ERROR_COLOR), underPrintView(Value::errorColor));
    assertEquals(new Color(AppPreferences.PRINT_NIL_COLOR), underPrintView(Value::nilColor));
    assertEquals(new Color(AppPreferences.PRINT_BUS_COLOR), underPrintView(Value::multiColor));
    assertEquals(
        new Color(AppPreferences.PRINT_STROKE_COLOR), underPrintView(Value::strokeColor));
  }

  @Test
  void componentInkUsesPrintPaletteWhenPrinting() {
    final var ink = new int[1];
    AppPreferences.runWithPrintViewColors(() -> ink[0] = AppPreferences.COMPONENT_COLOR.get());
    assertEquals(AppPreferences.DEFAULT_COMPONENT_COLOR, ink[0]);
  }

  /** Everything the print palette holds has to show up against paper. */
  @Test
  void thePrintPaletteIsReadableOnWhite() {
    for (final var color :
        new Color[] {
          underPrintView(Value::trueColor),
          underPrintView(Value::falseColor),
          underPrintView(Value::unknownColor),
          underPrintView(Value::errorColor),
          underPrintView(Value::strokeColor),
          underPrintView(Value::multiColor),
          new Color(AppPreferences.DEFAULT_COMPONENT_COLOR)
        }) {
      final var luminance =
          (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()) / 255;
      // The contrast ratio against white, by the usual definition, kept above the 3:1 that
      // applies to graphics rather than to text.
      final var contrast = 1.05 / (luminance + 0.05);
      assertTrue(contrast >= 3.0, color + " has only " + contrast + ":1 against white");
    }
  }

  /**
   * Darkening the palette must not collapse the states into one another.
   *
   * <p>These are the five a wire can be drawn in at once in the same figure. The nil colour is
   * left out: it marks a connection carrying no bits at all, not a value, and never appears
   * beside one.
   */
  @Test
  void thePrintPaletteKeepsTheStatesApart() {
    final var states =
        new Color[] {
          underPrintView(Value::trueColor),
          underPrintView(Value::falseColor),
          underPrintView(Value::unknownColor),
          underPrintView(Value::errorColor),
          underPrintView(Value::multiColor)
        };
    for (var i = 0; i < states.length; i++) {
      for (var j = i + 1; j < states.length; j++) {
        final var distance =
            Math.abs(states[i].getRed() - states[j].getRed())
                + Math.abs(states[i].getGreen() - states[j].getGreen())
                + Math.abs(states[i].getBlue() - states[j].getBlue());
        assertTrue(distance >= 60, states[i] + " and " + states[j] + " are only " + distance
            + " apart");
      }
    }
  }

  @Test
  void printViewAppliesOnlyWhileDrawingForPrint() {
    assertFalse(AppPreferences.inPrintView());
    AppPreferences.runWithPrintViewColors(() -> assertTrue(AppPreferences.inPrintView()));
    assertFalse(AppPreferences.inPrintView());
  }

  @Test
  void printViewIsGivenUpEvenWhenDrawingFails() {
    assertThrows(
        IllegalStateException.class,
        () ->
            AppPreferences.runWithPrintViewColors(
                () -> {
                  throw new IllegalStateException("drawing failed");
                }));
    assertFalse(AppPreferences.inPrintView(), "a failed export left every later drawing in print view");
  }
}
