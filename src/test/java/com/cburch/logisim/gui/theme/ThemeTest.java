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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.prefs.AppPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThemeTest {

  private final String originalMode = AppPreferences.THEME_MODE.get();

  @AfterEach
  void restoreMode() {
    AppPreferences.THEME_MODE.set(originalMode);
  }

  @Test
  void unknownOrMissingPreferenceFollowsTheSystem() {
    assertEquals(Theme.Mode.SYSTEM, Theme.Mode.fromKey(null));
    assertEquals(Theme.Mode.SYSTEM, Theme.Mode.fromKey(""));
    assertEquals(Theme.Mode.SYSTEM, Theme.Mode.fromKey("solarized"));
  }

  @Test
  void storedValuesRoundTripThroughTheirKeys() {
    for (final var mode : Theme.Mode.values()) {
      assertEquals(mode, Theme.Mode.fromKey(mode.key()), mode.name());
      assertEquals(mode.key(), mode.key().toLowerCase(java.util.Locale.ROOT));
    }
    assertEquals(Theme.Mode.DARK, Theme.Mode.fromKey(" Dark "));
  }

  @Test
  void anExplicitChoiceDoesNotConsultTheDesktop() {
    assertFalse(Theme.resolveDark(Theme.Mode.LIGHT));
    assertTrue(Theme.resolveDark(Theme.Mode.DARK));
  }

  @Test
  void isDarkFollowsTheStoredChoiceBeforeAnythingIsInstalled() {
    AppPreferences.THEME_MODE.set(AppPreferences.THEME_DARK);
    assertTrue(Theme.isDark());

    AppPreferences.THEME_MODE.set(AppPreferences.THEME_LIGHT);
    assertFalse(Theme.isDark());
  }

  @Test
  void themeModePreferenceOnlyAcceptsTheThreeModes() {
    assertEquals(3, AppPreferences.THEME_MODES.length);
    for (final var value : AppPreferences.THEME_MODES) {
      assertEquals(value, Theme.Mode.fromKey(value).key());
    }
  }

  @Test
  void tokensAnswerWithoutAThemeInstalled() {
    // Painting code reads these during tests and from the command-line tools, where no look and
    // feel is set; a null colour there would be a crash in a paint method.
    assertNotNull(Tokens.accent());
    assertNotNull(Tokens.error());
    assertNotNull(Tokens.warning());
    assertNotNull(Tokens.divider());
    assertNotNull(Tokens.iconForeground());
    assertNotNull(Tokens.statusBarBackground());
    assertNotNull(Tokens.canvasHalo());
  }

  @Test
  void failingListenerDoesNotStopTheOthers() {
    final var reached = new boolean[1];
    final Runnable boom =
        () -> {
          throw new IllegalStateException("listener under test");
        };
    final Runnable good = () -> reached[0] = true;
    Theme.addListener(boom);
    Theme.addListener(good);
    try {
      Theme.fireChanged();
    } finally {
      Theme.removeListener(boom);
      Theme.removeListener(good);
    }

    assertTrue(reached[0]);
  }
}
