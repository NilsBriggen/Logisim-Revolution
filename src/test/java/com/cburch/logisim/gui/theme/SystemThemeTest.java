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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
  void stalledStdoutIsBoundedAndProcessIsTerminated() throws Exception {
    final var process = startProbe("stall");
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
          assertNull(SystemTheme.readProbe(process, 150)));
      assertTrue(process.waitFor(2, TimeUnit.SECONDS));
      assertFalse(process.isAlive());
    } finally {
      process.destroyForcibly();
    }
  }

  @Test
  void excessiveOutputIsRejectedWithoutBlockingTheReader() throws Exception {
    final var process = startProbe("large");
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
          assertNull(SystemTheme.readProbe(process, 2_000)));
    } finally {
      process.destroyForcibly();
    }
  }

  @Test
  void successfulProbeReturnsItsOutput() throws Exception {
    final var process = startProbe("short");
    try {
      assertEquals("prefer-dark", SystemTheme.readProbe(process, 2_000));
    } finally {
      process.destroyForcibly();
    }
  }

  private static Process startProbe(String mode) throws Exception {
    final var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    final var classes = Path.of(Probe.class.getProtectionDomain().getCodeSource()
        .getLocation().toURI()).toString();
    return new ProcessBuilder(java, "-cp", classes, Probe.class.getName(), mode)
        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
  }

  /** A disposable child with no desktop or preference access. */
  public static class Probe {
    public static void main(String[] args) throws Exception {
      if (args[0].equals("stall")) Thread.sleep(30_000);
      if (args[0].equals("large")) System.out.print("x".repeat(100_000));
      if (args[0].equals("short")) System.out.print("prefer-dark");
    }
  }
}
