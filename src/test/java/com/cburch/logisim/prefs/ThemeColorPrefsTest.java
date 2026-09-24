/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.prefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The application used to overwrite every drawing colour whenever the theme changed, which threw
 * away any colour the user had picked. These tests pin the behaviour that replaced it.
 */
class ThemeColorPrefsTest {

  private static final int CHOSEN = 0xFF123456;

  private static final String KEY = AppPreferences.CANVAS_BG_COLOR.getIdentifier();
  private static final String LIGHT_KEY = "theme.light." + KEY;
  private static final String DARK_KEY = "theme.dark." + KEY;

  private String originalTheme;
  private String originalCurrent;
  private final Map<String, String> savedThemeKeys = new HashMap<>();

  /**
   * These tests write to the developer's own preferences, so everything they touch is copied
   * first and put back afterwards: resetting to defaults instead would throw away colours the
   * developer had chosen. The per-theme entries are also cleared so the tests do not depend on
   * what an earlier test, or an earlier run of the application, happened to leave behind.
   */
  @BeforeEach
  void takeOverPreferences() throws BackingStoreException {
    originalTheme = AppPreferences.THEME_MODE.get();
    originalCurrent = AppPreferences.getPrefs().get(KEY, null);
    for (final var key : AppPreferences.getPrefs().keys()) {
      for (final var prefix : ThemeColorPrefs.storagePrefixes()) {
        if (key.startsWith(prefix)) savedThemeKeys.put(key, AppPreferences.getPrefs().get(key, null));
      }
    }
    clearThemeKeys();
    ThemeColorPrefs.forgetLoadedTheme();
  }

  @AfterEach
  void restorePreferences() throws BackingStoreException {
    clearThemeKeys();
    for (final var saved : savedThemeKeys.entrySet()) {
      AppPreferences.getPrefs().put(saved.getKey(), saved.getValue());
    }
    if (originalCurrent == null) {
      AppPreferences.getPrefs().remove(KEY);
    } else {
      AppPreferences.getPrefs().put(KEY, originalCurrent);
    }
    AppPreferences.THEME_MODE.set(originalTheme);
    ThemeColorPrefs.forgetLoadedTheme();
    AppPreferences.applyThemeColors();
  }

  private static void clearThemeKeys() throws BackingStoreException {
    for (final var key : AppPreferences.getPrefs().keys()) {
      for (final var prefix : ThemeColorPrefs.storagePrefixes()) {
        if (key.startsWith(prefix)) AppPreferences.getPrefs().remove(key);
      }
    }
  }

  /**
   * Switches theme and loads that theme's colours, the way the application does.
   *
   * <p>The theme has to be installed, not merely preferred: once any look and feel has been
   * installed, {@code Theme.isDark()} answers from the installed one and ignores the preference,
   * so setting the preference alone leaves this depending on whichever test ran first.
   */
  private static void useTheme(String mode) {
    AppPreferences.THEME_MODE.set(mode);
    com.cburch.logisim.gui.theme.Theme.install();
    AppPreferences.applyThemeColors();
  }

  @Test
  void eachThemeGetsItsOwnShippedColours() {
    useTheme(AppPreferences.THEME_LIGHT);
    final var light = AppPreferences.CANVAS_BG_COLOR.get();

    useTheme(AppPreferences.THEME_DARK);
    final var dark = AppPreferences.CANVAS_BG_COLOR.get();

    assertEquals(AppPreferences.DEFAULT_CANVAS_BG_COLOR, light);
    assertEquals(AppPreferences.DARK_CANVAS_BG_COLOR, dark);
    assertNotEquals(light, dark);
  }

  @Test
  void colourChosenInOneThemeSurvivesSwitchingAwayAndBack() {
    useTheme(AppPreferences.THEME_DARK);
    AppPreferences.CANVAS_BG_COLOR.set(CHOSEN);

    useTheme(AppPreferences.THEME_LIGHT);
    assertEquals(
        AppPreferences.DEFAULT_CANVAS_BG_COLOR,
        AppPreferences.CANVAS_BG_COLOR.get(),
        "the light theme must not inherit a colour chosen for the dark one");

    useTheme(AppPreferences.THEME_DARK);
    assertEquals(CHOSEN, AppPreferences.CANVAS_BG_COLOR.get());
  }

  @Test
  void resetToDefaultsOnlyTouchesTheThemeShowing() {
    useTheme(AppPreferences.THEME_DARK);
    AppPreferences.CANVAS_BG_COLOR.set(CHOSEN);
    useTheme(AppPreferences.THEME_LIGHT);
    AppPreferences.CANVAS_BG_COLOR.set(CHOSEN);

    AppPreferences.setDefaultGridColors();

    assertEquals(AppPreferences.DEFAULT_CANVAS_BG_COLOR, AppPreferences.CANVAS_BG_COLOR.get());
    useTheme(AppPreferences.THEME_DARK);
    assertEquals(CHOSEN, AppPreferences.CANVAS_BG_COLOR.get(), "the other theme was reset too");
  }

  @Test
  void theValueColoursFollowTheThemeThatIsShowing() {
    useTheme(AppPreferences.THEME_LIGHT);
    final var light = com.cburch.logisim.data.Value.trueColor();

    useTheme(AppPreferences.THEME_DARK);
    final var dark = com.cburch.logisim.data.Value.trueColor();

    assertEquals(new java.awt.Color(AppPreferences.DEFAULT_TRUE_COLOR), light);
    assertEquals(new java.awt.Color(AppPreferences.DARK_TRUE_COLOR), dark);
  }

  /**
   * A change announced by the preference store is not a choice the user made.
   *
   * <p>Such a notification arrives on another thread after the fact and carries no value, so a
   * monitor answering it falls back to the default it was constructed with — for the drawing
   * colours, the light one. Recording that as a choice used to file the light colour under
   * whichever theme was showing, which then outlived every later change to the shipped palette:
   * the dark canvas kept drawing components in the light theme's ink.
   */
  @Test
  void changeAnnouncedByStoreIsNotRecordedAsChoice() {
    useTheme(AppPreferences.THEME_DARK);

    AppPreferences.firePropertyChangeFromStore(
        KEY, AppPreferences.DARK_CANVAS_BG_COLOR, AppPreferences.DEFAULT_CANVAS_BG_COLOR);

    assertNull(
        AppPreferences.getPrefs().get(DARK_KEY, null),
        "an echo from the store was filed as a colour chosen for the dark theme");
  }

  /** The same, for the theme that is not showing. */
  @Test
  void changeAnnouncedByStoreLeavesOtherThemeAlone() {
    useTheme(AppPreferences.THEME_LIGHT);

    AppPreferences.firePropertyChangeFromStore(KEY, 0, CHOSEN);

    assertNull(AppPreferences.getPrefs().get(LIGHT_KEY, null));
    assertNull(AppPreferences.getPrefs().get(DARK_KEY, null));
  }

  /**
   * A colour preference is written to the store the first time it is read, so without this a new
   * shipped default would only ever be seen by someone who had never run the program before.
   */
  @Test
  void newShippedColourReachesInstallationHoldingTheOldOne() {
    final var retired = 0xFF2B2B2B;
    AppPreferences.getPrefs().putInt(KEY, retired);
    ThemeColorPrefs.forgetLoadedTheme();

    useTheme(AppPreferences.THEME_DARK);

    assertEquals(AppPreferences.DARK_CANVAS_BG_COLOR, AppPreferences.CANVAS_BG_COLOR.get());
  }

  @Test
  void everyColourThatDiffersBetweenThemesIsRegistered() {
    AppPreferences.applyThemeColors();

    // A colour left out of the table would silently go back to sharing one value between themes.
    assertTrue(ThemeColorPrefs.registeredCount() >= 24, "registered: "
        + ThemeColorPrefs.registeredCount());
  }

  @Test
  void newTextUsesThemeInkAndRetainsSeparateExplicitOverrides() {
    useTheme(AppPreferences.THEME_LIGHT);
    assertEquals(AppPreferences.DEFAULT_TEXT_TOOL_COLOR, AppPreferences.TEXT_TOOL_COLOR.get());
    useTheme(AppPreferences.THEME_DARK);
    assertEquals(AppPreferences.DARK_TEXT_TOOL_COLOR, AppPreferences.TEXT_TOOL_COLOR.get());
    AppPreferences.TEXT_TOOL_COLOR.set(CHOSEN);
    useTheme(AppPreferences.THEME_LIGHT);
    assertEquals(AppPreferences.DEFAULT_TEXT_TOOL_COLOR, AppPreferences.TEXT_TOOL_COLOR.get());
    useTheme(AppPreferences.THEME_DARK);
    assertEquals(CHOSEN, AppPreferences.TEXT_TOOL_COLOR.get());
  }

  @Test
  void legacyExplicitTextColourIsPreservedInBothThemes() {
    final var prefs = AppPreferences.getPrefs();
    final var key = AppPreferences.TEXT_TOOL_COLOR.getIdentifier();
    for (final var color : new int[] {CHOSEN, AppPreferences.DEFAULT_TEXT_TOOL_COLOR}) {
      prefs.remove("textToolThemeMigrated");
      prefs.remove("theme.light." + key);
      prefs.remove("theme.dark." + key);
      prefs.putInt(key, color);
      AppPreferences.migrateTextToolColor();
      assertEquals(color, prefs.getInt("theme.light." + key, -1));
      assertEquals(color, prefs.getInt("theme.dark." + key, -1));
    }
  }
}
