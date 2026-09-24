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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.shell.SettingsNav;
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
}
