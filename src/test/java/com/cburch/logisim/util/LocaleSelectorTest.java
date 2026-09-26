/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cburch.logisim.prefs.AppPreferences;
import java.awt.event.ActionEvent;
import java.util.Locale;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocaleSelectorTest {
  private Locale savedLocale;
  private String savedPreference;

  @BeforeEach
  void save() {
    savedLocale = LocaleManager.getLocale();
    savedPreference = AppPreferences.LOCALE.get();
  }

  @AfterEach
  void restore() throws Exception {
    SwingUtilities.invokeAndWait(() -> LocaleManager.setLocale(savedLocale));
    AppPreferences.LOCALE.set(savedPreference);
  }

  @Test
  void movingTheSelectionDoesNotApplyALanguageButEnterDoes() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          LocaleManager.setLocale(Locale.ENGLISH);
          final var selector =
              new LocaleSelector(new Locale[] {Locale.GERMAN, Locale.ENGLISH, Locale.FRENCH});
          assertEquals(1, selector.getSelectedIndex(), "the language in use is preselected");

          selector.setSelectedIndex(0);
          selector.setSelectedIndex(2);
          assertEquals(Locale.ENGLISH, LocaleManager.getLocale(), "browsing must not apply");

          final var apply = selector.getActionMap().get(LocaleSelector.APPLY_ACTION);
          assertNotNull(apply);
          apply.actionPerformed(new ActionEvent(selector, ActionEvent.ACTION_PERFORMED, ""));
          assertEquals(Locale.FRENCH.getLanguage(), LocaleManager.getLocale().getLanguage());
        });
  }

  @Test
  void countryVariantOfTheLanguageInUseIsPreselected() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          LocaleManager.setLocale(Locale.ENGLISH);
          // setLocale normalises to an offered locale, so model the variant through the list.
          final var selector =
              new LocaleSelector(new Locale[] {Locale.GERMAN, Locale.US, Locale.FRENCH});
          assertEquals(1, selector.getSelectedIndex());
        });
  }
}
