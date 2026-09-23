/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.theme;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Reads the operating system's light or dark preference.
 *
 * <p>Every desktop exposes this differently and none of them through Java, so each platform is
 * asked in its own way and anything unexpected simply means "do not know". The parsing is kept
 * separate from running the commands so it can be tested without a desktop.
 */
public final class SystemTheme {

  /** How long any one probe may take before it is abandoned. */
  private static final long TIMEOUT_SECONDS = 2;

  private SystemTheme() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /**
   * Whether the system is set to a dark appearance.
   *
   * @return {@code true} for dark, {@code false} for light, empty when it cannot be determined
   */
  public static Optional<Boolean> isSystemDark() {
    final var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("mac")) {
      return parseMacOs(run("defaults", "read", "-g", "AppleInterfaceStyle"));
    }
    if (os.contains("win")) {
      return parseWindowsRegistry(
          run(
              "reg",
              "query",
              "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
              "/v",
              "AppsUseLightTheme"));
    }
    return linuxDark();
  }

  /** Asks the three Linux sources in turn, stopping at the first that answers. */
  private static Optional<Boolean> linuxDark() {
    final var colorScheme =
        parseGnomeColorScheme(
            run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme"));
    if (colorScheme.isPresent()) return colorScheme;

    final var gtkTheme =
        parseGtkTheme(run("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme"));
    if (gtkTheme.isPresent()) return gtkTheme;

    return parseKdeGlobals(readKdeGlobals());
  }

  /** macOS only defines the key while dark is active, so any successful read means dark. */
  static Optional<Boolean> parseMacOs(String output) {
    if (output == null) return Optional.empty();
    return Optional.of(output.toLowerCase(Locale.ROOT).contains("dark"));
  }

  /** The registry value is {@code AppsUseLightTheme}, so zero means dark. */
  static Optional<Boolean> parseWindowsRegistry(String output) {
    if (output == null) return Optional.empty();
    for (final var line : output.split("\\R")) {
      final var trimmed = line.trim();
      if (!trimmed.startsWith("AppsUseLightTheme")) continue;
      final var index = trimmed.lastIndexOf("0x");
      if (index < 0) continue;
      try {
        return Optional.of(Integer.parseInt(trimmed.substring(index + 2).trim(), 16) == 0);
      } catch (NumberFormatException ignored) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  /**
   * Reads the GNOME color scheme. {@code default} means the user has expressed no preference,
   * which is not the same as asking for light, so it answers "do not know".
   */
  static Optional<Boolean> parseGnomeColorScheme(String output) {
    if (output == null) return Optional.empty();
    final var value = unquote(output).toLowerCase(Locale.ROOT);
    if (value.contains("prefer-dark")) return Optional.of(true);
    if (value.contains("prefer-light")) return Optional.of(false);
    return Optional.empty();
  }

  /** The GTK theme name is a last resort: only a name saying "dark" is taken as an answer. */
  static Optional<Boolean> parseGtkTheme(String output) {
    if (output == null) return Optional.empty();
    final var value = unquote(output).toLowerCase(Locale.ROOT);
    if (value.isEmpty()) return Optional.empty();
    return value.endsWith("-dark") || value.contains("dark") ? Optional.of(true) : Optional.empty();
  }

  /** Reads the {@code ColorScheme} entry from the {@code [General]} group of kdeglobals. */
  static Optional<Boolean> parseKdeGlobals(List<String> lines) {
    if (lines == null) return Optional.empty();
    var inGeneral = false;
    for (final var line : lines) {
      final var trimmed = line.trim();
      if (trimmed.startsWith("[")) {
        inGeneral = trimmed.equalsIgnoreCase("[General]");
        continue;
      }
      if (!inGeneral) continue;
      final var separator = trimmed.indexOf('=');
      if (separator < 0) continue;
      if (!trimmed.substring(0, separator).trim().equalsIgnoreCase("ColorScheme")) continue;
      final var value = trimmed.substring(separator + 1).trim().toLowerCase(Locale.ROOT);
      if (value.isEmpty()) return Optional.empty();
      return Optional.of(value.contains("dark"));
    }
    return Optional.empty();
  }

  private static List<String> readKdeGlobals() {
    final var home = System.getProperty("user.home");
    if (home == null) return null;
    final var path = Path.of(home, ".config", "kdeglobals");
    try {
      return Files.exists(path) ? Files.readAllLines(path) : null;
    } catch (Exception ignored) {
      // An unreadable or oddly encoded file is simply not an answer.
      return null;
    }
  }

  /** Strips the surrounding quotes gsettings puts around string values. */
  private static String unquote(String value) {
    var result = value.trim();
    if (result.length() >= 2 && result.startsWith("'") && result.endsWith("'")) {
      result = result.substring(1, result.length() - 1);
    }
    return result.trim();
  }

  /**
   * Runs a probe, returning its output, or {@code null} if it fails, times out, or is not
   * installed. Probes only ever read a setting, so a failure is never worth reporting.
   */
  private static String run(String... command) {
    Process process = null;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(false).start();
      final var output = new StringBuilder();
      try (final var reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) output.append(line).append('\n');
      }
      if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) return null;
      return process.exitValue() == 0 ? output.toString() : null;
    } catch (Exception ignored) {
      return null;
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
    }
  }
}
