/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.std.Builtin;
import com.cburch.logisim.std.bfh.BfhLibrary;
import com.cburch.logisim.std.hdl.HdlLibrary;
import com.cburch.logisim.std.ttl.TtlLibrary;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every {@link com.cburch.logisim.comp.ComponentFactory} of every library in {@link Builtin} must
 * resolve, via {@link ComponentHelp}, to a target that actually exists as a {@code mapID} in {@code
 * doc/map_en.jhm}. Otherwise F1 / "Help for &lt;X&gt;" would open a blank or broken help page.
 */
class ComponentHelpTest {

  private static final Path MAP_FILE = Path.of("src", "main", "resources", "doc", "map_en.jhm");
  private static final Pattern MAP_ID_TARGET = Pattern.compile("<mapID\\s+target=\"([^\"]+)\"");

  /**
   * Libraries that (as of today) have no per-component help pages at all — the SoC library apart
   * from {@code SocBus}, the TTL library, the BFH-Praktika library, and the HDL-IP library — so
   * every one of their factories is expected to fall back to the library's own index page (or, for
   * BFH-Praktika/HDL-IP, to the top-level Library Reference, since those two libraries do not even
   * have a dedicated index page). This is intentional, not a resolution failure.
   */
  private static final Set<String> LIBRARIES_ALLOWED_TO_FALL_BACK_WHOLESALE =
      Set.of(TtlLibrary._ID, BfhLibrary._ID, HdlLibrary._ID, "Soc");

  /**
   * Individual factories, in otherwise fully-documented libraries, that intentionally have no
   * dedicated help page and so fall back to their library's index page.
   */
  private static final Set<String> FACTORIES_ALLOWED_TO_FALL_BACK =
      Set.of(
          "FPArithmetic/FPConstant",
          "Input/Output-Extra/ProgrammableGenerator",
          "Base/Image",
          "Arithmetic/Absolute",
          "Arithmetic/Exponentiator",
          "Arithmetic/MinMax",
          "Arithmetic/SquareRoot");

  private static Set<String> loadValidHelpTargets() throws IOException {
    final var text = Files.readString(MAP_FILE, StandardCharsets.UTF_8);
    final var targets = new HashSet<String>();
    final Matcher matcher = MAP_ID_TARGET.matcher(text);
    while (matcher.find()) {
      targets.add(matcher.group(1));
    }
    return targets;
  }

  @Test
  void everyBuiltinFactoryResolvesToAnExistingHelpTarget() throws IOException {
    final var validTargets = loadValidHelpTargets();
    assertTrue(validTargets.contains("libs"), "sanity check: map file must define the 'libs' target");

    final var builtin = new Builtin();
    final var unexpectedFallbacks = new TreeSet<String>();

    for (final Library lib : builtin.getLibraries()) {
      final var libraryIndexTarget = ComponentHelp.getHelpTarget(lib);
      final var allowedWholesaleFallback =
          LIBRARIES_ALLOWED_TO_FALL_BACK_WHOLESALE.contains(lib.getName());

      for (final Tool tool : lib.getTools()) {
        if (!(tool instanceof AddTool addTool)) continue;
        final var factory = addTool.getFactory();
        final var key = lib.getName() + "/" + factory.getName();
        final var target = ComponentHelp.getHelpTarget(factory, lib.getName());

        assertTrue(
            validTargets.contains(target),
            () ->
                "No mapID for help target '"
                    + target
                    + "' resolved for factory '"
                    + key
                    + "' — add an override in ComponentHelp or a mapID in map_en.jhm");

        final var fellBackToIndex = target.equals(libraryIndexTarget);
        if (fellBackToIndex && !allowedWholesaleFallback && !FACTORIES_ALLOWED_TO_FALL_BACK.contains(key)) {
          unexpectedFallbacks.add(key);
        }
      }
    }

    assertTrue(
        unexpectedFallbacks.isEmpty(),
        () ->
            "These factories unexpectedly fall back to their library's index page; either add a"
                + " dedicated ComponentHelp override, or add them to FACTORIES_ALLOWED_TO_FALL_BACK"
                + " if that is intentional: "
                + unexpectedFallbacks);
  }

  @Test
  void librariesAllowedToFallBackWholesaleAreActuallyKnownLibraries() {
    final var builtin = new Builtin();
    final var names = new HashSet<String>();
    for (final Library lib : builtin.getLibraries()) {
      names.add(lib.getName());
    }
    for (final var allowed : LIBRARIES_ALLOWED_TO_FALL_BACK_WHOLESALE) {
      assertTrue(names.contains(allowed), "unknown library id in allow-list: " + allowed);
    }
  }
}
