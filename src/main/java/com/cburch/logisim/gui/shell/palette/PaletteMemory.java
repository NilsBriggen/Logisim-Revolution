/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell.palette;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The components the palette keeps at the top: the ones pinned, and the ones used last.
 *
 * <p>Both are lists of component keys held in a preference as one string. The rules for reading
 * and writing that string are here, without a window, because the awkward parts — a list that
 * must not grow without limit, a component that is already in it, a preference left over from a
 * library that is no longer installed — are the parts worth testing.
 */
public final class PaletteMemory {

  /** How many recently used components the palette offers before forgetting the oldest. */
  public static final int RECENT_LIMIT = 12;

  private static final String SEPARATOR = "\n";

  /** Joins the two halves of a key. A library is part of it: two libraries may share a name. */
  private static final String KEY_SEPARATOR = "/";

  private PaletteMemory() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /**
   * The key a component is remembered under.
   *
   * <p>Built from the library's and the component's identifiers, which never change because they
   * are written into every project file that uses them.
   */
  public static String key(String libraryId, String toolId) {
    return safe(libraryId) + KEY_SEPARATOR + safe(toolId);
  }

  /** Reads a stored list, ignoring blank and repeated entries. */
  public static List<String> decode(String stored) {
    if (stored == null || stored.isBlank()) return List.of();
    final Set<String> keys = new LinkedHashSet<>();
    for (final var entry : stored.split(SEPARATOR)) {
      final var trimmed = entry.trim();
      if (!trimmed.isEmpty()) keys.add(trimmed);
    }
    return List.copyOf(keys);
  }

  /** Writes a list back out. */
  public static String encode(List<String> keys) {
    return String.join(SEPARATOR, keys);
  }

  /** The list with {@code key} moved to the front, no longer than {@link #RECENT_LIMIT}. */
  public static List<String> withMostRecent(List<String> recents, String key) {
    final var updated = new ArrayList<String>(recents.size() + 1);
    updated.add(key);
    for (final var existing : recents) {
      if (updated.size() >= RECENT_LIMIT) break;
      if (!existing.equals(key)) updated.add(existing);
    }
    return List.copyOf(updated);
  }

  /** The list with {@code key} added if it was absent, or removed if it was present. */
  public static List<String> toggled(List<String> keys, String key) {
    if (keys.contains(key)) {
      final var updated = new ArrayList<>(keys);
      updated.remove(key);
      return List.copyOf(updated);
    }
    final var updated = new ArrayList<>(keys);
    updated.add(key);
    return List.copyOf(updated);
  }

  /**
   * The stored keys that still name a component this project has.
   *
   * <p>A library can be uninstalled, and a project that never had it should not show gaps where
   * its components used to be.
   */
  public static List<String> retaining(List<String> keys, Set<String> available) {
    return keys.stream().filter(available::contains).toList();
  }

  private static String safe(String part) {
    if (part == null) return "";
    return String.join(" ", Arrays.stream(part.split("[\\n" + KEY_SEPARATOR + "]"))
        .filter(piece -> !piece.isBlank())
        .toArray(String[]::new));
  }
}
