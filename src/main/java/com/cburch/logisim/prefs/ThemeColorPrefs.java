/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.prefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps one set of drawing colours per theme.
 *
 * <p>The canvas, the component outlines and the signal values need different colours in a light
 * and a dark window, and the application used to handle that by overwriting every colour
 * preference each time the theme changed. That worked until someone picked a colour of their own:
 * the next theme change threw it away, and switching back did not bring it back.
 *
 * <p>Each colour is instead remembered twice, under a {@code theme.light.} and a {@code
 * theme.dark.} prefix. Switching theme saves the colours showing now and loads the other set, so
 * both themes keep whatever the user chose for them.
 */
public final class ThemeColorPrefs {

  /**
   * One colour, with the value it should have in each theme when the user has not chosen one.
   *
   * @param monitor the preference the application actually reads
   * @param lightDefault the shipped value for the light theme
   * @param darkDefault the shipped value for the dark theme
   */
  private record Entry(PrefMonitor<Integer> monitor, int lightDefault, int darkDefault) {

    /** Where this colour is remembered for one theme. */
    String storageKey(boolean dark) {
      return (dark ? "theme.dark." : "theme.light.") + monitor.getIdentifier();
    }

    int defaultFor(boolean dark) {
      return dark ? darkDefault : lightDefault;
    }
  }

  private static final List<Entry> entries = new ArrayList<>();

  /** Which theme's colours are loaded, or {@code null} before the first load. */
  private static Boolean loadedDark;

  /** Suppresses the mirroring listener while this class is the one doing the writing. */
  private static boolean loading;

  /**
   * What {@link #load} last put into each colour.
   *
   * <p>The {@code loading} flag alone is not enough to tell a value this class installed from one
   * the user chose, because a preference change can be announced after the call that caused it has
   * returned. Comparing against the loaded value does not depend on when the announcement arrives.
   */
  private static final Map<String, Integer> loadedValues = new HashMap<>();

  private ThemeColorPrefs() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /**
   * Which generation of the shipped palette the stored colours belong to.
   *
   * <p>A colour preference is written to disk the first time it is read, so a new shipped default
   * would never reach anyone who had already run the program. Raising this number retires the
   * stored colours once so that the new ones are seen.
   */
  private static final int PALETTE_VERSION = 7;

  static int shippedPaletteVersion() {
    return PALETTE_VERSION;
  }

  private static final String PALETTE_VERSION_KEY = "canvasPaletteVersion";

  /**
   * Drops stored colours belonging to an older palette.
   *
   * <p>Both the plain key and the two per-theme copies go: until the fix that goes with this
   * version, a preference change announced after {@link #load} had finished was mistaken for a
   * choice the user had made, so the per-theme copies cannot be trusted to hold anything but the
   * defaults that shipped at the time.
   */
  static synchronized void migrateShippedPalette() {
    final var prefs = getPrefs();
    if (prefs.getInt(PALETTE_VERSION_KEY, 0) >= PALETTE_VERSION) return;
    for (final var entry : entries) {
      // Only the per-theme copies: removing the plain key would have the monitor answer the
      // store's notification with the default it was constructed with, undoing the load.
      for (final var prefix : storagePrefixes()) prefs.remove(prefix + entry.monitor().getIdentifier());
    }
    prefs.putInt(PALETTE_VERSION_KEY, PALETTE_VERSION);
    loadedDark = null;
    loadedValues.clear();
  }

  /** Declares a colour that differs between the themes. Called from {@link AppPreferences}. */
  static void register(PrefMonitor<Integer> monitor, int lightDefault, int darkDefault) {
    entries.add(new Entry(monitor, lightDefault, darkDefault));
  }

  /**
   * Loads the colours belonging to {@code dark}.
   *
   * <p>Nothing is saved on the way out: a theme's entry exists only once the user has chosen a
   * colour for it, which is what {@link #mirrorChange} records. Writing the colours on screen back
   * on every switch would turn "never chosen" into "chosen, and happens to equal today's default",
   * and a later change to the shipped colours would then not reach anyone.
   */
  static synchronized void applyTheme(boolean dark) {
    if (loadedDark != null && loadedDark == dark) return;
    load(dark);
    loadedDark = dark;
  }

  /** Puts the shipped colours back for the theme showing now, leaving the other theme alone. */
  static synchronized void resetCurrentTheme() {
    final var dark = loadedDark != null && loadedDark;
    loading = true;
    try {
      for (final var entry : entries) {
        getPrefs().remove(entry.storageKey(dark));
        loadedValues.put(entry.monitor().getIdentifier(), entry.defaultFor(dark));
        entry.monitor().set(entry.defaultFor(dark));
      }
    } finally {
      loading = false;
    }
  }

  /**
   * Remembers a colour the user has just changed against the theme it was chosen for.
   *
   * <p>Called for every preference change, so it first has to work out whether the change was one
   * of these colours at all.
   */
  static synchronized void mirrorChange(String changedIdentifier) {
    if (loading || loadedDark == null || changedIdentifier == null) return;
    for (final var entry : entries) {
      if (!entry.monitor().getIdentifier().equals(changedIdentifier)) continue;
      final var value = entry.monitor().get();
      // A late echo of this class's own write is not a choice the user made.
      if (value.equals(loadedValues.get(changedIdentifier))) return;
      loadedValues.put(changedIdentifier, value);
      getPrefs().putInt(entry.storageKey(loadedDark), value);
      return;
    }
  }

  private static void load(boolean dark) {
    loading = true;
    try {
      for (final var entry : entries) {
        final var stored = getPrefs().getInt(entry.storageKey(dark), entry.defaultFor(dark));
        loadedValues.put(entry.monitor().getIdentifier(), stored);
        entry.monitor().set(stored);
      }
    } finally {
      loading = false;
    }
  }

  private static java.util.prefs.Preferences getPrefs() {
    return AppPreferences.getPrefs();
  }

  /** The colours registered as theme-dependent, for tests. */
  static int registeredCount() {
    return entries.size();
  }

  /** The prefixes under which per-theme colours are stored, for tests. */
  static String[] storagePrefixes() {
    return new String[] {"theme.light.", "theme.dark."};
  }

  /** Forgets which theme is loaded, so the next {@link #applyTheme} reloads. For tests. */
  static synchronized void forgetLoadedTheme() {
    loadedDark = null;
    loadedValues.clear();
  }
}
