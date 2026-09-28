/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.prefs;

import static com.cburch.logisim.gui.Strings.S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.shell.SettingsIndex;
import com.cburch.logisim.gui.shell.SettingsNav;
import com.cburch.logisim.gui.theme.Theme;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class PreferencesFrameSearchTest {
  @Test
  void actualWindowSettingsAreSearchableWithoutCreatingAFrame() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var window = new WindowOptions(null);
          window.localeChanged();
          final var page = PreferencesFrame.searchPage(window);

          assertEquals(window.getTitle(), page.title());
          assertTrue(SettingsNav.matches(page.text(), "scale"));
          assertTrue(SettingsNav.matches(page.text(), S.get("windowTheme")));
          assertTrue(SettingsNav.matches(page.text(), S.get("windowAppFont")));
          assertTrue(SettingsNav.matches(page.text(), S.get("windowToolbarZoomfactor")));
        });
  }

  @Test
  void everyControlOfTheWindowPageIsIndexedWithItsCurrentValue() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var window = new WindowOptions(null);
          window.localeChanged();

          final var settings = SettingsIndex.collect(window);
          final var titles = settings.stream().map(SettingsIndex.Setting::title).toList();

          final var theme =
              settings.stream()
                  .filter(setting -> setting.title().equals(S.get("windowTheme").replace(":", "")))
                  .findFirst()
                  .orElseThrow(() -> new AssertionError(titles.toString()));
          assertFalse(theme.value().isEmpty(), "the chosen theme is shown");
          assertTrue(
              theme.keywords().contains(WindowOptions.themeModeLabel(Theme.Mode.DARK)),
              "every theme is searchable: " + theme.keywords());
          assertTrue(titles.contains(S.get("windowToolbarZoomfactor")), titles.toString());
          assertTrue(titles.contains(S.get("layoutAntiAliasing")), titles.toString());
          assertTrue(titles.contains(S.get("windowToolbarReset")), titles.toString());
          assertTrue(titles.stream().noneMatch(String::isBlank));
        });
  }
}
