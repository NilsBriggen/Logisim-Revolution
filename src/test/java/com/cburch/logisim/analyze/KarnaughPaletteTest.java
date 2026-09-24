/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.prefs.AppPreferences;
import java.awt.Color;
import org.junit.jupiter.api.Test;

/**
 * The sixteen colours a Karnaugh map draws its covers in.
 *
 * <p>Telling one cover from another is the entire point of the map, so these have to stay apart —
 * which the dark set did not, because it was made by blending the light set toward white.
 */
class KarnaughPaletteTest {

  /** Manhattan distance in RGB below which two covers start to look like one. */
  private static final int MINIMUM_SEPARATION = 60;

  private static int separation(int first, int second) {
    final var a = new Color(first);
    final var b = new Color(second);
    return Math.abs(a.getRed() - b.getRed())
        + Math.abs(a.getGreen() - b.getGreen())
        + Math.abs(a.getBlue() - b.getBlue());
  }

  private static double luminance(int packed) {
    final var color = new Color(packed);
    return (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()) / 255;
  }

  @Test
  void bothThemesDeclareOneColourPerCover() {
    assertEquals(16, AppPreferences.DEFAULT_KMAP_COLORS.length);
    assertEquals(16, AppPreferences.DARK_KMAP_COLORS.length);
    assertEquals(16, AppPreferences.kmapColorMonitors().length);
  }

  @Test
  void everyCoverIsDistinguishableInTheDarkTheme() {
    assertSeparated(AppPreferences.DARK_KMAP_COLORS, "dark");
  }

  @Test
  void everyCoverIsDistinguishableInTheLightTheme() {
    assertSeparated(AppPreferences.DEFAULT_KMAP_COLORS, "light");
  }

  private static void assertSeparated(int[] palette, String which) {
    for (var i = 0; i < palette.length; i++) {
      for (var j = i + 1; j < palette.length; j++) {
        final var gap = separation(palette[i], palette[j]);
        assertTrue(
            gap >= MINIMUM_SEPARATION,
            which + " covers " + (i + 1) + " and " + (j + 1) + " are only " + gap + " apart");
      }
    }
  }

  /** A cover has to show up against the sheet it is drawn on. */
  @Test
  void theDarkCoversAreLightEnoughForADarkSheet() {
    for (var index = 0; index < AppPreferences.DARK_KMAP_COLORS.length; index++) {
      final var value = luminance(AppPreferences.DARK_KMAP_COLORS[index]);
      assertTrue(value > 0.15, "dark cover " + (index + 1) + " has luminance " + value);
    }
  }

  /** Each monitor's shipped default is its light colour, so nothing drifts out of step. */
  @Test
  void theMonitorsCarryTheLightPalette() {
    final var monitors = AppPreferences.kmapColorMonitors();
    for (var index = 0; index < monitors.length; index++) {
      assertTrue(monitors[index].getIdentifier().equals("KMAPColor" + (index + 1)),
          "monitor " + index + " is " + monitors[index].getIdentifier());
    }
  }
}
