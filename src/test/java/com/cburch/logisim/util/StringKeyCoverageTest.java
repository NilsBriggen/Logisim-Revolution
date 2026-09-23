/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every piece of text the program asks for has to exist.
 *
 * <p>A missing key is not a compile error and not an exception: the program simply shows the key
 * itself where a sentence should be, which nobody notices until a user does. This became worth
 * checking when the English wording was rewritten wholesale — the danger in that pass is renaming
 * a key rather than its value.
 */
class StringKeyCoverageTest {

  private static final Path SOURCE_ROOT = Path.of("src/main/java");

  private static final Path BUNDLE_ROOT =
      Path.of("src/main/resources/resources/logisim/strings");

  /** {@code S.get("key")} or {@code S.getter("key")}, with the key written out in full. */
  private static final Pattern LOOKUP =
      Pattern.compile("\\bS\\s*\\.\\s*(?:get|getter)\\s*\\(\\s*\"([^\"\\\\]*)\"");

  /** {@code new LocaleManager("resources/logisim", "gui")} inside a package's Strings class. */
  private static final Pattern BUNDLE_NAME =
      Pattern.compile("new\\s+LocaleManager\\s*\\(\\s*\"[^\"]*\"\\s*,\\s*\"([^\"]+)\"");

  /** {@code import static com.cburch.logisim.gui.Strings.S;} */
  private static final Pattern STATIC_IMPORT =
      Pattern.compile("import\\s+static\\s+([\\w.]+)\\.Strings\\.S\\s*;");

  /** {@code import com.cburch.draw.Strings;}, then used as {@code Strings.S.getter(...)}. */
  private static final Pattern PLAIN_IMPORT =
      Pattern.compile("import\\s+([\\w.]+)\\.Strings\\s*;");

  /** A package named in full at the point of use: {@code com.cburch.logisim.fpga.Strings.S}. */
  private static final Pattern QUALIFIED_USE =
      Pattern.compile("([\\w.]+)\\.Strings\\s*\\.\\s*S\\s*\\.");

  /** Block and line comments, which hold examples and disabled code rather than live lookups. */
  private static final Pattern COMMENT =
      Pattern.compile("/\\*[\\s\\S]*?\\*/|//[^\\n]*");

  private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");

  /** Maps a package that owns a Strings class to the bundle that class reads. */
  private static Map<String, String> bundleByPackage() throws IOException {
    final var bundles = new HashMap<String, String>();
    try (final var files = Files.walk(SOURCE_ROOT)) {
      files
          .filter(path -> path.getFileName().toString().equals("Strings.java"))
          .forEach(
              path -> {
                final var text = read(path);
                final var bundle = BUNDLE_NAME.matcher(text);
                final var pkg = PACKAGE.matcher(text);
                if (bundle.find() && pkg.find()) bundles.put(pkg.group(1), bundle.group(1));
              });
    }
    return bundles;
  }

  private static Properties bundle(String name) {
    final var properties = new Properties();
    final var path = BUNDLE_ROOT.resolve(name).resolve(name + ".properties");
    if (!Files.exists(path)) return properties;
    try (final var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      properties.load(reader);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    return properties;
  }

  private static String read(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  @Test
  void everyTextTheProgramAsksForExists() throws IOException {
    final var bundlesByPackage = bundleByPackage();
    assertFalse(bundlesByPackage.isEmpty(), "no Strings classes were found at all");

    final var loaded = new HashMap<String, Properties>();
    final var missing = new ArrayList<String>();
    var checked = 0;

    try (final var files = sources()) {
      for (final var path : files.toList()) {
        final var text = COMMENT.matcher(read(path)).replaceAll(" ");
        final var candidates = candidateBundles(text, bundlesByPackage);
        if (candidates.isEmpty()) continue;

        final var lookups = LOOKUP.matcher(text);
        while (lookups.find()) {
          final var key = lookups.group(1);
          if (key.isEmpty()) continue;
          checked++;
          var found = false;
          for (final var candidate : candidates) {
            if (loaded.computeIfAbsent(candidate, StringKeyCoverageTest::bundle)
                .containsKey(key)) {
              found = true;
              break;
            }
          }
          if (!found) {
            missing.add(SOURCE_ROOT.relativize(path) + ": " + key + " (in " + candidates + ")");
          }
        }
      }
    }

    assertTrue(checked > 1000, "only " + checked + " lookups were found; the scan is not working");
    assertTrue(missing.isEmpty(), "text the program asks for but no bundle has:\n"
        + String.join("\n", missing));
  }

  /**
   * Every bundle a file could be reading.
   *
   * <p>A file usually imports one {@code Strings.S} statically, but it may import the class
   * plainly, name another package's in full at the point of use, or simply be in the package that
   * declares one. A key found in any of them is a key the program will find.
   */
  private static Set<String> candidateBundles(String text, Map<String, String> bundlesByPackage) {
    final Set<String> candidates = new LinkedHashSet<>();
    for (final var pattern : List.of(STATIC_IMPORT, PLAIN_IMPORT, QUALIFIED_USE)) {
      final var matcher = pattern.matcher(text);
      while (matcher.find()) {
        final var bundle = bundlesByPackage.get(matcher.group(1));
        if (bundle != null) candidates.add(bundle);
      }
    }
    final var pkg = PACKAGE.matcher(text);
    if (pkg.find()) {
      final var bundle = bundlesByPackage.get(pkg.group(1));
      if (bundle != null) candidates.add(bundle);
    }
    return candidates;
  }

  /** No bundle should hold a key under two names, which is how a rename goes half-done. */
  @Test
  void noBundleRepeatsAValueUnderTwoKeysItAlsoUses() throws IOException {
    final var bundles = new HashSet<>(bundleByPackage().values());
    assertFalse(bundles.isEmpty());
    for (final var name : bundles) {
      final var path = BUNDLE_ROOT.resolve(name).resolve(name + ".properties");
      assertTrue(Files.exists(path), "no English bundle for " + name);
      final var seen = new HashSet<String>();
      final var repeated = new ArrayList<String>();
      for (final var line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
        final var trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
        final var equals = trimmed.indexOf('=');
        if (equals <= 0) continue;
        final var key = trimmed.substring(0, equals).trim();
        if (!seen.add(key)) repeated.add(name + ": " + key);
      }
      assertTrue(repeated.isEmpty(), "keys defined twice: " + repeated);
    }
  }

  private static Stream<Path> sources() throws IOException {
    return Files.walk(SOURCE_ROOT).filter(path -> path.toString().endsWith(".java"));
  }
}
