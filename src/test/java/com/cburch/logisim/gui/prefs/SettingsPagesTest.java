/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.prefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.generic.FormLayoutTestSupport;
import com.cburch.logisim.gui.generic.SettingsForm;
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.Container;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SettingsPagesTest {

  @Test
  void theAutosaveIntervalIsCommittedWhenTheFieldIsLeft() throws Exception {
    final var saved = AppPreferences.AUTOSAVE_INTERVAL.get();
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            AppPreferences.AUTOSAVE_INTERVAL.set(30);
            final var page = new AutosaveOptions(null);
            page.getIntervalField().setText("45");
            // Leaving the field commits, as Enter does.
            page.commitInterval();
            assertEquals(45, AppPreferences.AUTOSAVE_INTERVAL.get());
            page.getIntervalField().setText("not a number");
            page.commitInterval();
            assertEquals(45, AppPreferences.AUTOSAVE_INTERVAL.get());
            assertEquals("45", page.getIntervalField().getText(), "invalid text is put back");
          });
      // The focus listener that calls the commit is what the page relies on.
      SwingUtilities.invokeAndWait(
          () -> {
            final var page = new AutosaveOptions(null);
            final var field = page.getIntervalField();
            field.setText("50");
            for (final var listener : field.getFocusListeners()) {
              listener.focusLost(
                  new java.awt.event.FocusEvent(field, java.awt.event.FocusEvent.FOCUS_LOST));
            }
            assertEquals(50, AppPreferences.AUTOSAVE_INTERVAL.get());
          });
    } finally {
      AppPreferences.AUTOSAVE_INTERVAL.set(saved);
    }
  }

  @Test
  void theInterfaceScaleSliderShowsItsValueAndSnapsToQuarterSteps() throws Exception {
    final var savedScale = AppPreferences.getPrefs().getDouble("Scale", Double.NaN);
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            final var page = new WindowOptions(null);
            final var slider = page.getZoomSlider();
            assertTrue(slider.getSnapToTicks());
            assertEquals(WindowOptions.ZOOM_STEP, slider.getMinorTickSpacing());
            slider.setValueIsAdjusting(true);
            slider.setValue(150);
            assertEquals("150%", page.getZoomReadout());
          });
    } finally {
      if (Double.isNaN(savedScale)) {
        AppPreferences.getPrefs().remove("Scale");
      } else {
        AppPreferences.getPrefs().putDouble("Scale", savedScale);
      }
    }
  }

  /** Opening the page lays the slider out, which snaps 160 to a tick; that is not a choice. */
  @Test
  void openingTheWindowPageNeverWritesTheScale() throws Exception {
    final var saved = AppPreferences.getPrefs().get("Scale", null);
    AppPreferences.getPrefs().remove("Scale");
    try {
      FormLayoutTestSupport.atScale(
          1.6,
          () -> {
            final var page = new WindowOptions(null);
            page.setSize(800, 600);
            FormLayoutTestSupport.layoutTree(page);
            assertEquals("160%", page.getZoomReadout());
          });
      assertNull(AppPreferences.getPrefs().get("Scale", null));
    } finally {
      if (saved == null) {
        AppPreferences.getPrefs().remove("Scale");
      } else {
        AppPreferences.getPrefs().put("Scale", saved);
      }
    }
  }

  /** Every preferences page is laid out by the one shared form. */
  @Test
  void everyPageUsesTheSharedFormLayout() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          for (final var tab : PreferencesFrame.TABS) {
            final var page = tab.factory().apply(null);
            assertTrue(contains(page, SettingsForm.class), tab.title() + " has no SettingsForm");
          }
        });
  }

  private static boolean contains(Container root, Class<?> type) {
    for (final var child : root.getComponents()) {
      if (type.isInstance(child)) return true;
      if (child instanceof Container container && contains(container, type)) return true;
    }
    return false;
  }
}
