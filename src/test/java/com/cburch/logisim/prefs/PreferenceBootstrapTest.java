/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.prefs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;

class PreferenceBootstrapTest {
  @Test
  void importCopiesCompatibleValuesOnlyOnceWithoutChangingSource() throws Exception {
    final var node = Preferences.userRoot().node(
        "revolution-test-" + UUID.randomUUID());
    try {
      final var source = node.node("evolution");
      final var target = node.node("revolution");
      source.put("locale", "de");
      source.put("theme", "dark");
      source.put("Scale", "1.6");
      source.put("recent0", "123;/tmp/sample.circ");
      source.putByteArray("hotkeyFileOpen", new byte[] {1, 3, 7});
      source.putInt("canvasPaletteVersion", ThemeColorPrefs.shippedPaletteVersion());
      source.put("theme.dark.canvasBgColor", "-14606046");
      source.put("windowWidth", "2560");
      source.put("LookAndFeel", "old.LaF");
      target.put("locale", "fr");

      assertTrue(PreferenceBootstrap.shouldOffer(source, target));
      PreferenceBootstrap.copyCompatible(source, target);
      PreferenceBootstrap.rememberDecision(target, true);

      assertEquals("fr", target.get("locale", null));
      assertEquals("de", source.get("locale", null));
      assertEquals("dark", target.get("theme", null));
      assertEquals("1.6", target.get("Scale", null));
      assertEquals("123;/tmp/sample.circ", target.get("recent0", null));
      assertArrayEquals(new byte[] {1, 3, 7}, target.getByteArray("hotkeyFileOpen", null));
      assertEquals("-14606046", target.get("theme.dark.canvasBgColor", null));
      assertNull(target.get("windowWidth", null));
      assertNull(target.get("LookAndFeel", null));
      assertFalse(PreferenceBootstrap.shouldOffer(source, target));
    } finally {
      node.removeNode();
    }
  }

  @Test
  void declineAndUnsupportedOldPaletteDoNotImportColors() throws Exception {
    final var node = Preferences.userRoot().node(
        "revolution-test-" + UUID.randomUUID());
    try {
      final var source = node.node("evolution");
      final var target = node.node("revolution");
      source.put("theme.dark.canvasBgColor", "123");
      source.putInt("canvasPaletteVersion", 6);
      source.put("Scale", "NaN");
      source.put("AppFont", "Dialog");
      PreferenceBootstrap.copyCompatible(source, target);
      assertNull(target.get("theme.dark.canvasBgColor", null));
      assertNull(target.get("Scale", null));
      assertEquals("Dialog", target.get("AppFont", null));
      PreferenceBootstrap.rememberDecision(target, false);
      assertFalse(PreferenceBootstrap.shouldOffer(source, target));
    } finally {
      node.removeNode();
    }
  }

  @Test
  void commandLineTasksDoNotTriggerAnImportPrompt() {
    assertTrue(PreferenceBootstrap.isInteractive(new String[0]));
    assertFalse(PreferenceBootstrap.isInteractive(new String[] {"--help"}));
    assertFalse(PreferenceBootstrap.isInteractive(new String[] {"--tty", "table"}));
    assertFalse(PreferenceBootstrap.isInteractive(new String[] {"--clear-prefs"}));
  }
}
