/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell.palette;

import static com.cburch.logisim.gui.Strings.S;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which group of the palette a component belongs in.
 *
 * <p>The built-in libraries are organised the way the program is built rather than the way a
 * circuit is drawn: a pin and a clock are filed under Wiring beside a splitter, an LED sits beside
 * a keyboard, and "Plexers" is a word most people meet for the first time here. The palette groups
 * them by what they are for instead.
 *
 * <p>This is a presentation layer and nothing more. Library identifiers, the structure of {@code
 * std/Builtin}, and what is written into a {@code .circ} are all untouched, and a component this
 * table says nothing about falls back to a group named after the library it came from — so a
 * library the program has never seen still appears, whole, under its own name.
 */
public final class PaletteCatalog {

  /** A group of the palette, in the order the groups are shown. */
  public enum Group {
    LOGIC("paletteGroupLogic"),
    ROUTING("paletteGroupRouting"),
    ARITHMETIC("paletteGroupArithmetic"),
    MEMORY("paletteGroupMemory"),
    INPUT_OUTPUT("paletteGroupInputOutput"),
    DISPLAYS("paletteGroupDisplays"),
    CHIPS("paletteGroupChips"),
    ADVANCED("paletteGroupAdvanced");

    private final String key;

    Group(String key) {
      this.key = key;
    }

    /** The group's name in the user's language. */
    public String title() {
      return S.get(key);
    }
  }

  /**
   * The group a whole library goes to when none of its components says otherwise.
   *
   * <p>Keyed on the library's identifier, which is fixed for ever because it is written into
   * every project file that uses the library.
   */
  private static final Map<String, Group> BY_LIBRARY =
      Map.ofEntries(
          Map.entry("gates", Group.LOGIC),
          Map.entry("plexers", Group.ROUTING),
          Map.entry("wiring", Group.ROUTING),
          Map.entry("arithmetic", Group.ARITHMETIC),
          Map.entry("fparithmetic", Group.ARITHMETIC),
          Map.entry("memory", Group.MEMORY),
          Map.entry("i/o", Group.INPUT_OUTPUT),
          Map.entry("input/output-extra", Group.INPUT_OUTPUT),
          Map.entry("ttl", Group.CHIPS),
          Map.entry("hdl-ip", Group.ADVANCED),
          Map.entry("tcl", Group.ADVANCED),
          Map.entry("bfh-praktika", Group.ADVANCED),
          Map.entry("soc", Group.ADVANCED));

  /**
   * Components whose group is not their library's.
   *
   * <p>Keyed on the component identifier, which is likewise fixed. Two libraries hold components
   * of more than one kind: Wiring mixes the things that carry a signal with the things that
   * produce or read one, and I/O mixes what a person operates with what a person reads.
   */
  private static final Map<String, Group> BY_TOOL =
      Map.ofEntries(
          // Wiring: sources and readouts rather than connections.
          Map.entry("pin", Group.INPUT_OUTPUT),
          Map.entry("clock", Group.INPUT_OUTPUT),
          Map.entry("probe", Group.INPUT_OUTPUT),
          Map.entry("constant", Group.INPUT_OUTPUT),
          Map.entry("power", Group.INPUT_OUTPUT),
          Map.entry("ground", Group.INPUT_OUTPUT),
          Map.entry("por", Group.INPUT_OUTPUT),
          // I/O: things that show a value rather than things a person operates.
          Map.entry("7-segment display", Group.DISPLAYS),
          Map.entry("hex digit display", Group.DISPLAYS),
          Map.entry("led", Group.DISPLAYS),
          Map.entry("rgbled", Group.DISPLAYS),
          Map.entry("ledbar", Group.DISPLAYS),
          Map.entry("dotmatrix", Group.DISPLAYS),
          Map.entry("rgb video", Group.DISPLAYS),
          Map.entry("tty", Group.DISPLAYS));

  private PaletteCatalog() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /**
   * The group a component belongs in.
   *
   * @param libraryId the identifier of the library the component came from
   * @param toolId the component's own identifier
   * @return the group, or empty when the palette has nothing to say and the library's own name
   *     should be used as the heading instead
   */
  public static Optional<Group> groupOf(String libraryId, String toolId) {
    if (toolId != null) {
      final var byTool = BY_TOOL.get(normalize(toolId));
      if (byTool != null) return Optional.of(byTool);
    }
    if (libraryId == null) return Optional.empty();
    return Optional.ofNullable(BY_LIBRARY.get(normalize(libraryId)));
  }

  /** Whether a library is one this table claims to organise. */
  public static boolean knowsLibrary(String libraryId) {
    return libraryId != null && BY_LIBRARY.containsKey(normalize(libraryId));
  }

  private static String normalize(String id) {
    return id.trim().toLowerCase(Locale.ROOT);
  }
}
