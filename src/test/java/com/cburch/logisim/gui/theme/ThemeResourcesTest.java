/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.theme;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatLaf;
import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The property files carry the whole look of the application. FlatLaf reads them by name from the
 * registered package and says nothing if one is absent, so their presence is checked here.
 */
class ThemeResourcesTest {

  private static final String PACKAGE = "com/cburch/logisim/gui/theme/";

  @Test
  void secondaryTextRemainsReadableOnItsActualShellSurfaces() {
    FlatLaf.registerCustomDefaultsSource("com.cburch.logisim.gui.theme");
    for (final var laf : List.of(new LogisimLightLaf(), new LogisimDarkLaf())) {
      final var defaults = laf.getDefaults();
      for (final var pair : List.of(
          List.of("Logisim.mutedForeground", "Panel.background"),
          List.of("Logisim.sidePanel.headerForeground", "Logisim.sidePanel.background"),
          List.of("Logisim.activityBar.foreground", "Logisim.activityBar.background"))) {
        final var first = luminance(defaults.getColor(pair.get(0)));
        final var second = luminance(defaults.getColor(pair.get(1)));
        final var contrast = (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
        assertTrue(contrast >= 4.5, laf.getName() + " " + pair + " contrast=" + contrast);
      }
    }
  }

  private static double luminance(Color color) {
    return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen())
        + 0.0722 * linear(color.getBlue());
  }

  private static double linear(int channel) {
    final var value = channel / 255.0;
    return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
  }

  private static String read(String name) throws IOException {
    final var url = ThemeResourcesTest.class.getClassLoader().getResource(PACKAGE + name);
    assertNotNull(url, name + " is missing");
    return new String(url.openStream().readAllBytes(), StandardCharsets.UTF_8);
  }

  @Test
  void bothThemesAndTheSharedFileArePresent() throws IOException {
    for (final var name :
        List.of("FlatLaf.properties", "LogisimLightLaf.properties", "LogisimDarkLaf.properties")) {
      assertFalse(read(name).isBlank(), name + " is empty");
    }
  }

  @Test
  void bothThemesDefineEveryRoleTheApplicationPaintsWith() throws IOException {
    final var light = read("LogisimLightLaf.properties");
    final var dark = read("LogisimDarkLaf.properties");
    final var roles =
        List.of(
            "Logisim.accent",
            "Logisim.warning",
            "Logisim.error",
            "Logisim.success",
            "Logisim.divider",
            "Logisim.mutedForeground",
            "Logisim.icon.foreground",
            "Logisim.icon.disabled",
            "Logisim.activityBar.background",
            "Logisim.activityBar.foreground",
            "Logisim.sidePanel.headerForeground",
            "Logisim.statusBar.background",
            "Logisim.statusBar.foreground",
            "Logisim.badge.background",
            "Logisim.toast.background",
            "Logisim.canvas.halo");

    final var missing = new ArrayList<String>();
    for (final var role : roles) {
      if (!light.contains(role + " =")) missing.add("light: " + role);
      if (!dark.contains(role + " =")) missing.add("dark: " + role);
    }

    assertTrue(missing.isEmpty(), "roles a theme does not define: " + missing);
  }

  @Test
  void theNameOfEachThemeMatchesItsPropertyFile() {
    // FlatLaf looks the file up by the class's simple name, so a rename must move the file too.
    assertTrue(LogisimLightLaf.class.getSimpleName().equals("LogisimLightLaf"));
    assertTrue(LogisimDarkLaf.class.getSimpleName().equals("LogisimDarkLaf"));
  }

  @Test
  void darkChromeSurfacesRemainCloseToPanelInsteadOfClampingToBlack() {
    FlatLaf.registerCustomDefaultsSource("com.cburch.logisim.gui.theme");
    final var defaults = new LogisimDarkLaf().getDefaults();
    final var panel = defaults.getColor("Panel.background");
    for (final var role :
        List.of("Logisim.activityBar.background", "Logisim.statusBar.background")) {
      final var surface = defaults.getColor(role);
      assertNotNull(surface, role);
      assertFalse(Color.BLACK.equals(surface), role + " was clamped to black");
      assertTrue(Math.abs(panel.getRed() - surface.getRed()) <= 12, role);
      assertTrue(Math.abs(panel.getGreen() - surface.getGreen()) <= 12, role);
      assertTrue(Math.abs(panel.getBlue() - surface.getBlue()) <= 12, role);
    }
  }
}
