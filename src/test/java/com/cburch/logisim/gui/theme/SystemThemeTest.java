/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The desktop probes are parsed rather than trusted, so the parsing is checked against the exact
 * output each platform produces. Anything unrecognised must read as "do not know" rather than as
 * light, or a user on a dark desktop gets a white window.
 */
class SystemThemeTest {

  @Test
  void macOsReportsDarkOnlyWhenTheKeyExists() {
    assertEquals(Optional.of(true), SystemTheme.parseMacOs("Dark\n"));
    // The key is absent in light mode, so the command fails and the output is null.
    assertEquals(Optional.empty(), SystemTheme.parseMacOs(null));
  }

  @Test
  void windowsReadsAppsUseLightThemeInverted() {
    final var dark =
        "\r\nHKEY_CURRENT_USER\\Software\\...\\Personalize\r\n"
            + "    AppsUseLightTheme    REG_DWORD    0x0\r\n";
    final var light =
        "\r\nHKEY_CURRENT_USER\\Software\\...\\Personalize\r\n"
            + "    AppsUseLightTheme    REG_DWORD    0x1\r\n";

    assertEquals(Optional.of(true), SystemTheme.parseWindowsRegistry(dark));
    assertEquals(Optional.of(false), SystemTheme.parseWindowsRegistry(light));
    assertEquals(Optional.empty(), SystemTheme.parseWindowsRegistry("ERROR: not found"));
    assertEquals(Optional.empty(), SystemTheme.parseWindowsRegistry(null));
  }

  @Test
  void gnomeDefaultColorSchemeIsNotAPreference() {
    assertEquals(Optional.of(true), SystemTheme.parseGnomeColorScheme("'prefer-dark'\n"));
    assertEquals(Optional.of(false), SystemTheme.parseGnomeColorScheme("'prefer-light'\n"));
    // "default" means the user has not said, which must not be read as a request for light.
    assertEquals(Optional.empty(), SystemTheme.parseGnomeColorScheme("'default'\n"));
  }

  @Test
  void gtkThemeNameOnlyAnswersWhenItSaysDark() {
    assertEquals(Optional.of(true), SystemTheme.parseGtkTheme("'Adwaita-dark'\n"));
    assertEquals(Optional.of(true), SystemTheme.parseGtkTheme("'Breeze Dark'\n"));
    assertEquals(Optional.empty(), SystemTheme.parseGtkTheme("'Adwaita'\n"));
    assertEquals(Optional.empty(), SystemTheme.parseGtkTheme("''\n"));
  }

  @Test
  void kdeGlobalsColorSchemeIsReadFromTheGeneralGroupOnly() {
    final var dark =
        List.of("[ColorEffects:Disabled]", "ColorScheme=SomethingLight", "[General]",
            "ColorScheme=BreezeDark", "Name=Breeze Dark");
    final var light = List.of("[General]", "ColorScheme=BreezeLight");

    assertEquals(Optional.of(true), SystemTheme.parseKdeGlobals(dark));
    assertEquals(Optional.of(false), SystemTheme.parseKdeGlobals(light));
    assertEquals(Optional.empty(), SystemTheme.parseKdeGlobals(List.of("[General]", "Name=x")));
    assertEquals(Optional.empty(), SystemTheme.parseKdeGlobals(null));
  }

  @Test
  void probingTheRealDesktopNeverThrows() {
    final var result = SystemTheme.isSystemDark();

    assertTrue(result.isPresent() || result.isEmpty(), "must answer or abstain, never fail");
  }
}
