/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell.palette;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Search metadata is keyed by stable tool IDs, independently of translated display names. */
public final class PaletteSearch {
  private static final Map<String, List<String>> ALIASES = Map.ofEntries(
      Map.entry("multiplexer", List.of("mux")),
      Map.entry("demultiplexer", List.of("demux")),
      Map.entry("pin", List.of("input pin", "output pin")),
      Map.entry("d flip-flop", List.of("dff", "flip flop")),
      Map.entry("t flip-flop", List.of("tff", "flip flop")),
      Map.entry("j-k flip-flop", List.of("jkff", "flip flop")),
      Map.entry("s-r flip-flop", List.of("srff", "flip flop")),
      Map.entry("plarom", List.of("pla rom")));

  private PaletteSearch() {}

  /** Smaller scores rank first; -1 means there is no match. */
  public static int score(String toolId, String displayName, String query) {
    final var needle = normalize(query);
    if (needle.isEmpty()) return 0;
    final var name = normalize(displayName);
    final var id = normalize(toolId);
    if (name.equals(needle) || id.equals(needle)) return 0;
    final var aliases = ALIASES.getOrDefault(id, List.of());
    if (aliases.contains(needle)) return 1;
    if (name.startsWith(needle) || id.startsWith(needle)) return 2;
    if (name.contains(needle) || id.contains(needle)) return 3;
    return aliases.stream().anyMatch(alias -> alias.contains(needle)) ? 4 : -1;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}

