/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.prefs;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.prefs.SimOptions.WirePalette;
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The colours a wire is drawn in, held to what a reader needs to tell them apart.
 *
 * <p>Wires are graphics, so each state needs 3:1 against the canvas it is drawn on (WCAG 1.4.11).
 * High and low are the two a reader distinguishes most, and must not differ by hue alone: they are
 * held to 3:1 between themselves too. The colour-blind presets must in addition keep the states apart
 * for readers with protanopia, deuteranopia or tritanopia, and exist once per theme: the old single
 * preset put a yellow high (1.23:1) on the light canvas and a black bus on the dark one.
 */
class SignalPaletteContrastTest {

  private static final double GRAPHIC_CONTRAST = 3.0;
  private static final double HIGH_LOW_CONTRAST = 3.0;

  /** Smallest sRGB distance between two states as a colour-blind reader sees them. */
  private static final double CVD_DISTANCE = 60;

  private static final Color LIGHT_CANVAS = new Color(AppPreferences.DEFAULT_CANVAS_BG_COLOR);
  private static final Color DARK_CANVAS = new Color(AppPreferences.DARK_CANVAS_BG_COLOR);

  private static final WirePalette SHIPPED_LIGHT =
      new WirePalette(
          AppPreferences.DEFAULT_TRUE_COLOR,
          AppPreferences.DEFAULT_FALSE_COLOR,
          AppPreferences.DEFAULT_UNKNOWN_COLOR,
          AppPreferences.DEFAULT_ERROR_COLOR,
          AppPreferences.DEFAULT_NIL_COLOR,
          AppPreferences.DEFAULT_BUS_COLOR,
          AppPreferences.DEFAULT_STROKE_COLOR);

  private static final WirePalette SHIPPED_DARK =
      new WirePalette(
          AppPreferences.DARK_TRUE_COLOR,
          AppPreferences.DARK_FALSE_COLOR,
          AppPreferences.DARK_UNKNOWN_COLOR,
          AppPreferences.DARK_ERROR_COLOR,
          AppPreferences.DARK_NIL_COLOR,
          AppPreferences.DARK_BUS_COLOR,
          AppPreferences.DARK_STROKE_COLOR);

  /** Machado, Oliveira and Fernandes (2009), full severity, applied to linear RGB. */
  private static final double[][][] DICHROMACIES = {
    {{0.152286, 1.052583, -0.204868}, {0.114503, 0.786281, 0.099216},
      {-0.003882, -0.048116, 1.051998}},
    {{0.367322, 0.860646, -0.227968}, {0.280085, 0.672501, 0.047413},
      {-0.011820, 0.042940, 0.968881}},
    {{1.255528, -0.076749, -0.178779}, {-0.078411, 0.930809, 0.147602},
      {0.004733, 0.691367, 0.303900}},
  };

  private static double linear(int channel) {
    final var c = channel / 255.0;
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  }

  private static double encoded(double linear) {
    final var v = Math.max(0, Math.min(1, linear));
    return 255 * (v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055);
  }

  private static double[] rgbLinear(int rgb) {
    return new double[] {linear(rgb >> 16 & 0xFF), linear(rgb >> 8 & 0xFF), linear(rgb & 0xFF)};
  }

  private static double luminance(int rgb) {
    final var c = rgbLinear(rgb);
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
  }

  private static double contrast(int a, int b) {
    final var la = luminance(a);
    final var lb = luminance(b);
    return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
  }

  private static double[] seenWith(double[][] matrix, int rgb) {
    final var c = rgbLinear(rgb);
    final var seen = new double[3];
    for (var i = 0; i < 3; i++) {
      seen[i] = encoded(matrix[i][0] * c[0] + matrix[i][1] * c[1] + matrix[i][2] * c[2]);
    }
    return seen;
  }

  private static double distance(double[] a, double[] b) {
    return Math.sqrt(
        Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
  }

  /** The five a wire can be drawn in side by side. Nil marks no bits and never sits beside one. */
  private static int[] states(WirePalette p) {
    return new int[] {
      p.trueColor(), p.falseColor(), p.unknownColor(), p.errorColor(), p.busColor()
    };
  }

  private static List<String> readability(String name, WirePalette palette, Color canvas) {
    final var failures = new ArrayList<String>();
    final var bg = canvas.getRGB();
    final int[] all = {
      palette.trueColor(), palette.falseColor(), palette.unknownColor(), palette.errorColor(),
      palette.nilColor(), palette.busColor(), palette.strokeColor()
    };
    final String[] names = {"true", "false", "unknown", "error", "nil", "bus", "stroke"};
    for (var i = 0; i < all.length; i++) {
      final var ratio = contrast(all[i], bg);
      // Nil marks a connection with no bits: it is drawn faint on purpose, but must still show.
      final var needed = "nil".equals(names[i]) ? 2.5 : GRAPHIC_CONTRAST;
      if (ratio < needed) {
        failures.add(String.format("%s %s %06X: %.2f:1 on its canvas", name, names[i],
            all[i] & 0xFFFFFF, ratio));
      }
    }
    final var highLow = contrast(palette.trueColor(), palette.falseColor());
    if (highLow < HIGH_LOW_CONTRAST) {
      failures.add(String.format("%s high/low only %.2f:1 apart", name, highLow));
    }
    return failures;
  }

  @Test
  void theShippedPalettesAreReadableOnTheirCanvas() {
    final var failures = new ArrayList<String>();
    failures.addAll(readability("light", SHIPPED_LIGHT, LIGHT_CANVAS));
    failures.addAll(readability("dark", SHIPPED_DARK, DARK_CANVAS));
    assertTrue(failures.isEmpty(), String.join("\n", failures));
  }

  @Test
  void theColorBlindPalettesAreReadableOnTheirCanvas() {
    final var failures = new ArrayList<String>();
    failures.addAll(readability("colour-blind light", SimOptions.colorBlindPalette(false),
        LIGHT_CANVAS));
    failures.addAll(readability("colour-blind dark", SimOptions.colorBlindPalette(true),
        DARK_CANVAS));
    assertTrue(failures.isEmpty(), String.join("\n", failures));
  }

  @Test
  void theColorBlindPalettesKeepTheStatesApartUnderDichromacy() {
    final var failures = new ArrayList<String>();
    for (final var dark : new boolean[] {false, true}) {
      final var states = states(SimOptions.colorBlindPalette(dark));
      for (final var matrix : DICHROMACIES) {
        for (var i = 0; i < states.length; i++) {
          for (var j = i + 1; j < states.length; j++) {
            final var d = distance(seenWith(matrix, states[i]), seenWith(matrix, states[j]));
            if (d < CVD_DISTANCE) {
              failures.add(String.format("%s: %06X and %06X only %.1f apart",
                  dark ? "dark" : "light", states[i], states[j], d));
            }
          }
        }
      }
    }
    assertTrue(failures.isEmpty(), String.join("\n", failures));
  }
}
