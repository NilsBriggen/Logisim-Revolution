/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.prefs;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.TestBase;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Guards the light and dark colour pairs in {@link AppPreferences}.
 *
 * <p>A colour added for one theme but not the other stays at its light value in dark mode, which is
 * how the chronogram ended up drawing black waveforms on a dark background. These tests fail as soon
 * as a pair drifts apart.
 */
public class ThemeColorPairingTest extends TestBase {

  /**
   * Colours that intentionally have no dark counterpart.
   *
   * <p>Keep this list short, and only for colours that carry meaning rather than following the
   * theme, or that are only ever used on a light surface such as an exported image.
   */
  private static final java.util.Set<String> NO_DARK_VARIANT =
      java.util.Set.of(
          // Status colours drawn over the board image in the FPGA mapping dialog. They mark state
          // rather than following the theme, and the image behind them is not themed either.
          "DEFAULT_FPGA_MAPPED_COLOR",
          "DEFAULT_FPGA_SELECTED_MAPPED_COLOR",
          "DEFAULT_FPGA_SELECTABLE_MAPPED_COLOR",
          "DEFAULT_FPGA_SELECT_COLOR",
          "DEFAULT_WIDTH_ERROR_COLOR",
          "DEFAULT_WIDTH_ERROR_CAPTION_COLOR",
          "DEFAULT_WIDTH_ERROR_HIGHLIGHT_COLOR",
          "DEFAULT_WIDTH_ERROR_BACKGROUND_COLOR",
          "DEFAULT_CLOCK_FREQUENCY_COLOR",
          "DEFAULT_FPGA_DEFINE_COLOR",
          "DEFAULT_FPGA_DEFINE_HIGHLIGHT_COLOR",
          "DEFAULT_FPGA_DEFINE_RESIZE_COLOR",
          "DEFAULT_FPGA_DEFINE_MOVE_COLOR",
          "DEFAULT_FPGA_DEFINE_MARK_COLOR");

  /** Returns the names of the int colour constants that start with the given prefix. */
  private static java.util.List<String> colorConstants(String prefix) {
    return Arrays.stream(AppPreferences.class.getDeclaredFields())
        .filter(f -> Modifier.isStatic(f.getModifiers()) && Modifier.isPublic(f.getModifiers()))
        .filter(f -> f.getType() == int.class)
        .map(java.lang.reflect.Field::getName)
        .filter(name -> name.startsWith(prefix) && name.endsWith("_COLOR"))
        .collect(Collectors.toList());
  }

  @Test
  public void everyThemedColorHasBothVariants() {
    final var darkNames = colorConstants("DARK_");
    final var missing = new ArrayList<String>();

    for (final var defaultName : colorConstants("DEFAULT_")) {
      if (NO_DARK_VARIANT.contains(defaultName)) continue;
      final var expected = "DARK_" + defaultName.substring("DEFAULT_".length());
      if (!darkNames.contains(expected)) missing.add(defaultName + " has no " + expected);
    }

    assertTrue(
        missing.isEmpty(),
        "colours without a dark counterpart (add one, or list it in NO_DARK_VARIANT): " + missing);
  }

  /** Every dark colour must have the light one it overrides. */
  @Test
  public void everyDarkColorHasALightCounterpart() {
    final var defaultNames = colorConstants("DEFAULT_");
    final var missing = new ArrayList<String>();

    for (final var darkName : colorConstants("DARK_")) {
      final var expected = "DEFAULT_" + darkName.substring("DARK_".length());
      if (!defaultNames.contains(expected)) missing.add(darkName + " has no " + expected);
    }

    assertTrue(missing.isEmpty(), "dark colours without a light counterpart: " + missing);
  }

  /** The chronogram colours are the ones this guard was written for. */
  @Test
  public void chronogramColorsArePaired() {
    final var darkNames = colorConstants("DARK_");
    for (final var name : colorConstants("DEFAULT_")) {
      if (!name.startsWith("DEFAULT_CHRONO_")) continue;
      final var expected = "DARK_" + name.substring("DEFAULT_".length());
      assertTrue(darkNames.contains(expected), name + " is missing " + expected);
    }
  }
}
